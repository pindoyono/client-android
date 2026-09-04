package com.smkn2malinau.absensi.device

import com.smkn2malinau.absensi.data.remote.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Test alur registrasi device via Google — port `oauth_server.proses_oauth_token`.
 * ApiService di-fake; tidak butuh Google/Android.
 */
class DeviceRegistrarTest {

    // ID token dummy: header.payload.signature — payload = {"email":"..."}
    private fun idTokenUntuk(email: String): String {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"email":"$email"}""".toByteArray())
        return "eyJhbGciOiJSUzI1NiJ9.$payload.sig"
    }

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `registrasi sukses - kembalikan device_id dan api_key`() = runTest {
        val api = FakeApi(
            login = GoogleLoginResponse("jwt-123", "Pak Budi", "admin"),
            register = DeviceRegisterResponse(deviceId = "gerbang-utama-01", rawApiKey = "KEY-abc")
        )
        val hasil = DeviceRegistrar(api).registrasi(
            idTokenUntuk("budi@smkn2malinau.sch.id"), "android-xyz", "Gerbang Utama"
        )

        assertTrue(hasil is HasilRegistrasi.Sukses)
        hasil as HasilRegistrasi.Sukses
        assertEquals("gerbang-utama-01", hasil.deviceId)
        assertEquals("KEY-abc", hasil.apiKey)
        assertEquals("Pak Budi", hasil.nama)
        assertEquals("Bearer jwt-123", api.bearerDipakai)
    }

    @Test
    fun `api_key dibaca dari field apapun (api_key)`() = runTest {
        val api = FakeApi(
            login = GoogleLoginResponse("jwt", null, null),
            register = DeviceRegisterResponse(deviceId = "d1", apiKey = "via-api-key")
        )
        val hasil = DeviceRegistrar(api).registrasi(idTokenUntuk("a@guru.smk.belajar.id"), "d1", "X")
        assertEquals("via-api-key", (hasil as HasilRegistrasi.Sukses).apiKey)
    }

    @Test
    fun `domain tidak diizinkan - tidak memanggil server`() = runTest {
        val api = FakeApi()
        val hasil = DeviceRegistrar(api).registrasi(
            idTokenUntuk("orang@gmail.com"), "d1", "X"
        )
        assertTrue(hasil is HasilRegistrasi.DomainTidakDiizinkan)
        assertEquals(0, api.loginCalls)
    }

    @Test
    fun `device sudah terdaftar (409) - minta api key manual`() = runTest {
        val api = FakeApi(
            login = GoogleLoginResponse("jwt", "Bu Sri", "guru_piket"),
            registerError = httpError(409)
        )
        val hasil = DeviceRegistrar(api).registrasi(
            idTokenUntuk("sri@admin.smk.belajar.id"), "android-xyz", "Aula"
        )
        assertTrue(hasil is HasilRegistrasi.SudahTerdaftar)
        assertEquals("android-xyz", (hasil as HasilRegistrasi.SudahTerdaftar).deviceId)
    }

    @Test
    fun `login ditolak server (403) - domain tidak diizinkan`() = runTest {
        val api = FakeApi(loginError = httpError(403))
        val hasil = DeviceRegistrar(api).registrasi(
            idTokenUntuk("x@smkn2malinau.sch.id"), "d1", "X"
        )
        assertTrue(hasil is HasilRegistrasi.DomainTidakDiizinkan)
    }

    @Test
    fun `server tidak kembalikan api_key - gagal`() = runTest {
        val api = FakeApi(
            login = GoogleLoginResponse("jwt", null, null),
            register = DeviceRegisterResponse(deviceId = "d1")
        )
        val hasil = DeviceRegistrar(api).registrasi(idTokenUntuk("a@smkn2malinau.sch.id"), "d1", "X")
        assertTrue(hasil is HasilRegistrasi.Gagal)
    }

    @Test
    fun `domain guard - case insensitive & trim`() {
        assertTrue(DeviceRegistrar.domainDiizinkan("A@SMKN2MALINAU.SCH.ID"))
        assertTrue(DeviceRegistrar.domainDiizinkan("guru@guru.smk.belajar.id"))
        assertTrue(!DeviceRegistrar.domainDiizinkan("guru@belajar.id"))
        assertTrue(!DeviceRegistrar.domainDiizinkan("bukan-email"))
    }

    private class FakeApi(
        private val login: GoogleLoginResponse? = null,
        private val loginError: HttpException? = null,
        private val register: DeviceRegisterResponse? = null,
        private val registerError: HttpException? = null,
    ) : ApiService {
        var loginCalls = 0
        var bearerDipakai: String? = null

        override suspend fun loginGoogle(request: GoogleLoginRequest): GoogleLoginResponse {
            loginCalls++
            loginError?.let { throw it }
            return login ?: error("login tidak di-set")
        }

        override suspend fun registerDevice(bearer: String, request: DeviceRegisterRequest): DeviceRegisterResponse {
            bearerDipakai = bearer
            registerError?.let { throw it }
            return register ?: error("register tidak di-set")
        }

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
