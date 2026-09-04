package com.smkn2malinau.absensi.auth

import android.util.Log
import com.smkn2malinau.absensi.data.local.dao.AkunDao
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.data.remote.ApiService
import com.smkn2malinau.absensi.data.remote.GoogleLoginRequest
import com.smkn2malinau.absensi.device.GoogleIdToken
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.security.PasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import java.time.LocalDateTime

data class SesiPengguna(
    val identitas: String,
    val nama: String,
    val role: Role,
    val siswaId: Int? = null,
) {
    fun boleh(fitur: Fitur) = HakAkses.boleh(role, fitur)
}

sealed interface HasilLogin {
    data class Sukses(val sesi: SesiPengguna) : HasilLogin
    /** Google terverifikasi tapi akun belum punya password lokal — minta user membuatnya. */
    data class ButuhPassword(val identitas: String, val nama: String, val role: Role) : HasilLogin
    data class Gagal(val pesan: String) : HasilLogin
}

/**
 * Auth Panel Admin berbasis role, offline-first:
 * - **Online**: Google Sign-In → server `/auth/login/google` → role. Akun di-upsert lokal.
 * - **Offline**: identitas (email/NIS) + password → cocokkan hash di `akun_lokal`.
 * Server tidak menyimpan password; akun & hash hanya di device (SQLCipher).
 */
class AuthRepository(
    private val akunDao: AkunDao,
    private val credentialManager: CredentialManager,
    private val api: ApiService,
) {
    private val _sesi = MutableStateFlow(credentialManager.getSesiTersimpan()?.let {
        SesiPengguna(it.identitas, it.nama, Role.dari(it.role), it.siswaId)
    })
    val sesi: StateFlow<SesiPengguna?> = _sesi.asStateFlow()

    // ---------- Login ----------

    suspend fun loginGoogle(idToken: String): HasilLogin {
        val email = GoogleIdToken.email(idToken)?.lowercase()
            ?: return HasilLogin.Gagal("Token Google tidak memuat email.")
        val namaGoogle = GoogleIdToken.payload(idToken)?.get("name")?.takeIf { !it.isJsonNull }?.asString

        val resp = try {
            api.loginGoogle(GoogleLoginRequest(idToken))
        } catch (e: HttpException) {
            return when (e.code()) {
                401, 403 -> HasilLogin.Gagal("Akun $email belum terdaftar sebagai guru di server. Hubungi admin sekolah.")
                else -> HasilLogin.Gagal("Login server gagal (HTTP ${e.code()}).")
            }
        } catch (e: Exception) {
            return HasilLogin.Gagal("Tidak bisa menghubungi server — pakai login offline (email + password).")
        }

        val role = Role.dari(resp.role)
        val emailFinal = resp.email?.takeIf { it.isNotBlank() }?.lowercase() ?: email
        val nama = resp.nama?.takeIf { it.isNotBlank() } ?: namaGoogle ?: emailFinal
        upsertDariServer(emailFinal, nama, role)
        mulaiSesi(SesiPengguna(emailFinal, nama, role))
        return HasilLogin.Sukses(_sesi.value!!)
    }

    suspend fun loginPassword(identitas: String, password: String): HasilLogin {
        val id = identitas.trim().lowercase()
        val akun = akunDao.getByIdentitas(id)
            ?: return HasilLogin.Gagal("Akun '$identitas' tidak ada di device ini.")
        if (akun.password_hash == null) {
            return HasilLogin.ButuhPassword(akun.identitas, akun.nama, Role.dari(akun.role))
        }
        if (!PasswordHasher.verifikasi(password, akun.password_hash, akun.salt)) {
            return HasilLogin.Gagal("Password salah.")
        }
        val sesi = SesiPengguna(akun.identitas, akun.nama, Role.dari(akun.role), akun.siswa_id)
        mulaiSesi(sesi)
        return HasilLogin.Sukses(sesi)
    }

    /** Buat password untuk akun (setelah [HasilLogin.ButuhPassword]) lalu langsung login. */
    suspend fun buatPasswordLaluLogin(identitas: String, password: String): HasilLogin {
        if (password.length < 6) return HasilLogin.Gagal("Password minimal 6 karakter.")
        val id = identitas.trim().lowercase()
        val akun = akunDao.getByIdentitas(id) ?: return HasilLogin.Gagal("Akun tidak ditemukan.")
        val h = PasswordHasher.hash(password)
        akunDao.setPassword(id, h.hashB64, h.saltB64, waktu())
        return loginPassword(id, password)
    }

    fun logout() {
        credentialManager.clearSesi()
        _sesi.value = null
    }

    // ---------- Manajemen akun (admin) ----------

    suspend fun daftarAkun(): List<AkunLokal> = akunDao.getSemua()

    suspend fun adaAkun(): Boolean = akunDao.countAktif() > 0

    /**
     * Seed akun dari server saat device di-setup lewat Google (role ditentukan server).
     * Password di-set terpisah bila diberikan (agar bisa login offline nanti).
     */
    suspend fun seedDariServer(email: String, nama: String, role: Role, password: String? = null) {
        val id = email.trim().lowercase()
        val lama = akunDao.getByIdentitas(id)
        val h = password?.takeIf { it.length >= 6 }?.let { PasswordHasher.hash(it) }
        akunDao.upsert(
            AkunLokal(
                identitas = id, nama = nama.ifBlank { id }, role = role.kode,
                password_hash = h?.hashB64 ?: lama?.password_hash,
                salt = h?.saltB64 ?: lama?.salt,
                siswa_id = lama?.siswa_id,
                diperbarui_pada = waktu(),
            )
        )
    }

    suspend fun tambahAkun(identitas: String, nama: String, role: Role, password: String?, siswaId: Int?): String? {
        val id = identitas.trim().lowercase()
        if (id.isBlank()) return "Identitas (email / NIS) wajib diisi."
        if (role != Role.SISWA && !id.contains('@')) return "Email guru/admin harus mengandung '@'."
        if (password != null && password.length < 6) return "Password minimal 6 karakter."
        val h = password?.let { PasswordHasher.hash(it) }
        akunDao.upsert(
            AkunLokal(
                identitas = id, nama = nama.ifBlank { id }, role = role.kode,
                password_hash = h?.hashB64, salt = h?.saltB64, siswa_id = siswaId,
                diperbarui_pada = waktu(),
            )
        )
        return null
    }

    suspend fun setPassword(identitas: String, passwordBaru: String): String? {
        if (passwordBaru.length < 6) return "Password minimal 6 karakter."
        val id = identitas.trim().lowercase()
        akunDao.getByIdentitas(id) ?: return "Akun tidak ditemukan."
        val h = PasswordHasher.hash(passwordBaru)
        akunDao.setPassword(id, h.hashB64, h.saltB64, waktu())
        return null
    }

    suspend fun nonaktifkanAkun(identitas: String): String? {
        val id = identitas.trim().lowercase()
        val akun = akunDao.getByIdentitas(id) ?: return "Akun tidak ditemukan."
        if (akun.role == Role.ADMIN.kode && akunDao.countAdminAktif() <= 1) {
            return "Tidak bisa menonaktifkan admin terakhir."
        }
        akunDao.nonaktifkan(id, waktu())
        if (_sesi.value?.identitas == id) logout()
        return null
    }

    // ---------- internal ----------

    private suspend fun upsertDariServer(email: String, nama: String, role: Role) {
        val lama = akunDao.getByIdentitas(email)
        akunDao.upsert(
            AkunLokal(
                identitas = email,
                nama = nama,
                role = role.kode,
                password_hash = lama?.password_hash, // pertahankan password offline yang sudah ada
                salt = lama?.salt,
                siswa_id = lama?.siswa_id,
                diperbarui_pada = waktu(),
            )
        )
    }

    private fun mulaiSesi(sesi: SesiPengguna) {
        credentialManager.saveSesi(sesi.identitas, sesi.nama, sesi.role.kode, sesi.siswaId)
        _sesi.value = sesi
        Log.i("AuthRepository", "Sesi dimulai: ${sesi.identitas} (${sesi.role.kode})")
    }

    private fun waktu() = LocalDateTime.now().toString()
}
