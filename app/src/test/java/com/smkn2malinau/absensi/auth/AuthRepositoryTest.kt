package com.smkn2malinau.absensi.auth

import com.smkn2malinau.absensi.data.local.dao.AkunDao
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.data.remote.*
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.security.PasswordHasher
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AuthRepositoryTest {

    private val cm = mockk<CredentialManager>(relaxed = true).also {
        every { it.getSesiTersimpan() } returns null
    }

    private fun repo(dao: AkunDao, api: ApiService = FakeApi()) = AuthRepository(dao, cm, api)

    private fun fakeIdToken(email: String, nama: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"email":"$email","name":"$nama"}""".toByteArray())
        return "eyJhbGciOiJIUzI1NiJ9.$payload.sig"
    }

    @Test
    fun `login offline - password benar - sesi dengan role benar`() = runBlocking {
        val h = PasswordHasher.hash("guru123")
        val dao = FakeAkunDao(
            AkunLokal("guru@smkn2malinau.sch.id", "Bu Guru", "guru_piket", h.hashB64, h.saltB64, diperbarui_pada = "now")
        )
        val hasil = repo(dao).loginPassword("GURU@smkn2malinau.sch.id", "guru123")
        assertTrue(hasil is HasilLogin.Sukses)
        assertEquals(Role.GURU_PIKET, (hasil as HasilLogin.Sukses).sesi.role)
    }

    @Test
    fun `login offline - password salah - gagal`() = runBlocking {
        val h = PasswordHasher.hash("benar")
        val dao = FakeAkunDao(AkunLokal("a@b.c", "A", "admin", h.hashB64, h.saltB64, diperbarui_pada = "now"))
        assertTrue(repo(dao).loginPassword("a@b.c", "salah") is HasilLogin.Gagal)
    }

    @Test
    fun `login offline - akun tanpa password - ButuhPassword`() = runBlocking {
        val dao = FakeAkunDao(AkunLokal("a@b.c", "A", "admin", null, null, diperbarui_pada = "now"))
        assertTrue(repo(dao).loginPassword("a@b.c", "apa") is HasilLogin.ButuhPassword)
    }

    @Test
    fun `login google - upsert akun lokal dengan role server`() = runBlocking {
        val dao = FakeAkunDao()
        val api = FakeApi(GoogleLoginResponse(accessToken = "jwt", nama = "Pak Admin", role = "admin"))
        val hasil = repo(dao, api).loginGoogle(fakeIdToken("admin@smkn2malinau.sch.id", "Pak Admin"))
        assertTrue(hasil is HasilLogin.Sukses)
        assertEquals(Role.ADMIN, (hasil as HasilLogin.Sukses).sesi.role)
        assertEquals("admin", dao.get("admin@smkn2malinau.sch.id")?.role)
    }

    @Test
    fun `seed dari server dengan password - bisa login offline`() = runBlocking {
        val dao = FakeAkunDao()
        val r = repo(dao)
        r.seedDariServer("kepala@smkn2malinau.sch.id", "Kepala", Role.ADMIN, "kepala123")
        val hasil = r.loginPassword("kepala@smkn2malinau.sch.id", "kepala123")
        assertEquals(Role.ADMIN, (hasil as HasilLogin.Sukses).sesi.role)
    }

    @Test
    fun `seed dari server tanpa password - login offline minta ButuhPassword`() = runBlocking {
        val dao = FakeAkunDao()
        val r = repo(dao)
        r.seedDariServer("guru@smkn2malinau.sch.id", "Guru", Role.GURU_PIKET)
        assertTrue(r.loginPassword("guru@smkn2malinau.sch.id", "apa") is HasilLogin.ButuhPassword)
    }

    @Test
    fun `tidak bisa nonaktifkan admin terakhir`() = runBlocking {
        val h = PasswordHasher.hash("x123456")
        val dao = FakeAkunDao(AkunLokal("only@a.c", "Only", "admin", h.hashB64, h.saltB64, diperbarui_pada = "now"))
        assertTrue(repo(dao).nonaktifkanAkun("only@a.c")!!.contains("admin terakhir"))
    }

    // --- fakes ---

    private class FakeAkunDao(vararg awal: AkunLokal) : AkunDao {
        private val data = awal.associateBy { it.identitas }.toMutableMap()
        fun get(id: String) = data[id]
        override suspend fun getByIdentitas(identitas: String) = data[identitas]?.takeIf { it.aktif == 1 }
        override suspend fun getByIdentitasApaPun(identitas: String) = data[identitas]
        override suspend fun getSemua() = data.values.filter { it.aktif == 1 }
        override suspend fun upsert(akun: AkunLokal) { data[akun.identitas] = akun }
        override suspend fun nonaktifkan(identitas: String, waktu: String) {
            data[identitas]?.let { data[identitas] = it.copy(aktif = 0) }
        }
        override suspend fun setPassword(identitas: String, hash: String, salt: String, waktu: String) {
            data[identitas]?.let { data[identitas] = it.copy(password_hash = hash, salt = salt) }
        }
        override suspend fun countAdminAktif() = data.values.count { it.aktif == 1 && it.role == "admin" }
        override suspend fun countAktif() = data.values.count { it.aktif == 1 }
    }

    private class FakeApi(private val resp: GoogleLoginResponse? = null) : ApiService {
        override suspend fun loginGoogle(request: GoogleLoginRequest) =
            resp ?: error("no google resp")
        override suspend fun registerDevice(bearer: String, request: DeviceRegisterRequest) = error("n/a")
        override suspend fun syncAbsensi(request: SyncAbsensiRequest) = error("n/a")
        override suspend fun getEmbeddings(diperbaruiSejak: String?) = error("n/a")
        override suspend fun getJadwalEfektif(kelas: String?) = error("n/a")
        override suspend fun getDispensasiAktif(tanggal: String) = error("n/a")
        override suspend fun pushOverride(request: PushOverrideRequest) = error("n/a")
        override suspend fun reportHealth(deviceId: String, request: HealthReportRequest) = error("n/a")
        override suspend fun getRoster() = error("n/a")
        override suspend fun getSiswaRoster(kelas: String?, enrolled: Boolean?) = error("n/a")
    }
}
