package com.smkn2malinau.absensi.device

import com.smkn2malinau.absensi.data.remote.ApiService
import com.smkn2malinau.absensi.data.remote.DeviceRegisterRequest
import com.smkn2malinau.absensi.data.remote.GoogleLoginRequest
import retrofit2.HttpException

/**
 * Registrasi device via Google — port alur `app/device/oauth_server.py` (Windows).
 *
 * Alur:
 *   1. Google ID token (didapat dari [GoogleIdTokenProvider]) → `POST /auth/login/google` → JWT.
 *   2. `POST /device/register` (Bearer JWT) → `raw_api_key`.
 *   3. Pemanggil menyimpan `device_id` + `api_key` lewat CredentialManager.
 *
 * Kelas ini murni logika jaringan supaya bisa diuji tanpa Android/Google.
 */
class DeviceRegistrar(private val api: ApiService) {

    suspend fun registrasi(
        idToken: String,
        deviceId: String?,
        namaLokasi: String,
    ): HasilRegistrasi {
        // Guard domain di sisi client (verifikasi tetap di server).
        val email = GoogleIdToken.email(idToken)
        if (email != null && !domainDiizinkan(email)) {
            return HasilRegistrasi.DomainTidakDiizinkan(email)
        }

        val login = try {
            api.loginGoogle(GoogleLoginRequest(idToken))
        } catch (e: HttpException) {
            return when (e.code()) {
                401, 403 -> HasilRegistrasi.DomainTidakDiizinkan(email ?: "")
                else -> HasilRegistrasi.Gagal("Login server gagal (HTTP ${e.code()})")
            }
        } catch (e: Exception) {
            return HasilRegistrasi.Gagal("Koneksi server gagal: ${e.message}")
        }

        if (login.accessToken.isBlank()) {
            return HasilRegistrasi.Gagal("Server tidak mengembalikan token")
        }

        // Cek role SEBELUM memanggil registerDevice — server hanya menerima token
        // "admin" untuk /device/register (bukan cuma guru/guru_piket, apalagi
        // siswa yang kini juga bisa login Google). Tanpa guard ini, mencoba
        // registrasi pakai akun non-admin gagal dengan HTTP 401/403 polos yang
        // tidak menjelaskan sebabnya sama sekali.
        if (login.role != "admin") {
            return HasilRegistrasi.Gagal(
                "Akun ini login sebagai '${login.role ?: "tidak diketahui"}', bukan admin. " +
                    "Registrasi device kiosk cuma bisa pakai akun Google admin sekolah."
            )
        }

        val reg = try {
            api.registerDevice(
                bearer = "Bearer ${login.accessToken}",
                request = DeviceRegisterRequest(
                    deviceId = deviceId,
                    namaLokasi = namaLokasi.ifBlank { "Gerbang Utama" },
                    platform = "android",
                )
            )
        } catch (e: HttpException) {
            return when (e.code()) {
                409 -> HasilRegistrasi.SudahTerdaftar(
                    deviceId = deviceId ?: "",
                    nama = login.nama,
                    role = login.role,
                )
                401, 403 -> HasilRegistrasi.Gagal(
                    "Server menolak akun ini untuk registrasi device (role '${login.role}'). " +
                        "Pakai akun Google admin sekolah."
                )
                else -> HasilRegistrasi.Gagal("Registrasi device gagal (HTTP ${e.code()})")
            }
        } catch (e: Exception) {
            return HasilRegistrasi.Gagal("Registrasi device gagal: ${e.message}")
        }

        val apiKey = reg.apiKeyEfektif
            ?: return HasilRegistrasi.Gagal("Registrasi berhasil tapi server tidak mengembalikan api_key")

        return HasilRegistrasi.Sukses(
            deviceId = reg.deviceId.ifBlank { deviceId ?: "" },
            apiKey = apiKey,
            faceKey = reg.faceEncryptionKey?.takeIf { it.isNotBlank() },
            nama = login.nama,
            role = login.role,
        )
    }

    companion object {
        /** PRD: hanya email dari domain sekolah yang boleh mendaftarkan device. */
        val DOMAIN_DIIZINKAN = setOf(
            "smkn2malinau.sch.id",
            "guru.smk.belajar.id",
            "admin.smk.belajar.id",
        )

        fun domainDiizinkan(email: String): Boolean {
            val domain = email.substringAfter('@', "").lowercase().trim()
            return domain in DOMAIN_DIIZINKAN
        }
    }
}

sealed class HasilRegistrasi {
    data class Sukses(
        val deviceId: String,
        val apiKey: String,
        val nama: String?,
        val role: String?,
        /** Fernet key embedding dari server (PRD R-P1-1); null bila server versi lama. */
        val faceKey: String? = null,
    ) : HasilRegistrasi()

    /**
     * Device sudah terdaftar di server (409) tapi api key tidak ada di perangkat ini.
     * UI harus meminta admin menulis ulang api key (dari dashboard).
     */
    data class SudahTerdaftar(
        val deviceId: String,
        val nama: String?,
        val role: String?,
    ) : HasilRegistrasi()

    data class DomainTidakDiizinkan(val email: String) : HasilRegistrasi()

    data class Gagal(val pesan: String) : HasilRegistrasi()
}
