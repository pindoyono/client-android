package com.smkn2malinau.absensi

import com.smkn2malinau.absensi.data.remote.ApiService
import com.smkn2malinau.absensi.data.remote.DeviceAuthInterceptor
import com.smkn2malinau.absensi.data.remote.SyncAbsensiRequest
import com.smkn2malinau.absensi.data.remote.AbsensiRecordDto
import com.smkn2malinau.absensi.security.RateLimiter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Test API client dengan MockWebServer — verifikasi header auth + error handling.
 * PRD bagian 4 & 8.
 */
class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiService

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .addInterceptor(DeviceAuthInterceptor("device_01", "test_api_key_1234567890"))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(ApiService::class.java)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    // Verifikasi header X-Device-Id + X-Device-Api-Key terkirim
    @Test
    fun `header auth terkirim di semua panggilan`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"total":0,"disimpan":0,"duplikat":0,"gagal":0,"hasil":[]}"""))

        api.syncAbsensi(
            SyncAbsensiRequest(
                records = listOf(
                    AbsensiRecordDto(
                        recordId = "r1", siswaId = 1, tanggal = "2024-01-15",
                        type = "MASUK", jamAktual = "2024-01-15T06:30:00",
                        statusKehadiranOtomatis = "NORMAL", catatan = null,
                        deviceId = "device_01"
                    )
                )
            )
        )

        val request = server.takeRequest()
        assertEquals("device_01", request.getHeader("X-Device-Id"))
        assertEquals("test_api_key_1234567890", request.getHeader("X-Device-Api-Key"))
    }

    // Test negatif: 401 ditangani tanpa crash
    @Test
    fun `response 401 ditangani tanpa crash`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

        try {
            api.syncAbsensi(SyncAbsensiRequest(records = emptyList()))
            fail("Harusnya throw exception")
        } catch (e: Exception) {
            // Expected — 401 harus ditangani
            assertTrue(e is retrofit2.HttpException)
        }
    }

    // Test negatif: 403 ditangani tanpa crash
    @Test
    fun `response 403 ditangani tanpa crash`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))

        try {
            api.syncAbsensi(SyncAbsensiRequest(records = emptyList()))
            fail("Harusnya throw exception")
        } catch (e: Exception) {
            assertTrue(e is retrofit2.HttpException)
        }
    }

    // Test: field aktif dari /embeddings/sync terbaca benar (kontrak server: {server_time, jumlah, data})
    @Test
    fun `field aktif dari embeddings sync terbaca`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"server_time":"2026-09-04T00:00:00","jumlah":2,"data":[{"siswa_id":1,"nis":"23145","nama":"Ahmad","kelas":"XI-E","aktif":true,"embedding_encrypted":"0a0b","model_version":"v1"},{"siswa_id":2,"nis":"23146","nama":"Budi","kelas":"XI-E","aktif":false,"embedding_encrypted":"0a0b","model_version":"v1"}]}"""
            )
        )

        val response = api.getEmbeddings(null)
        assertEquals(2, response.data.size)
        assertTrue(response.data[0].aktif)
        assertFalse(response.data[1].aktif)
    }
}
