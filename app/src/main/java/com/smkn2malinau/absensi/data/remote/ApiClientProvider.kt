package com.smkn2malinau.absensi.data.remote

import com.google.gson.GsonBuilder
import com.smkn2malinau.absensi.BuildConfig
import com.smkn2malinau.absensi.security.RateLimiter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration

object ApiClientProvider {
    /** Base URL default dari BuildConfig (diisi lewat local.properties SERVER_BASE_URL=). */
    val BASE_URL: String = normalisasi(BuildConfig.SERVER_BASE_URL.ifBlank { "https://absen.smkn2malinau.sch.id/" })

    private val gson = GsonBuilder().setLenient().create()

    // Kiosk sering di koneksi lemah; `GET /embeddings/sync` bisa menarik MB dan
    // `POST /absensi/sync` mengirim batch. Default OkHttp (10 dtk read) terlalu
    // ketat untuk itu — request valid gagal padahal server sedang memproses.
    private fun OkHttpClient.Builder.timeoutKiosk() = apply {
        connectTimeout(Duration.ofSeconds(20))
        readTimeout(Duration.ofSeconds(60))
        writeTimeout(Duration.ofSeconds(60))
        retryOnConnectionFailure(true)
    }

    private fun normalisasi(url: String): String {
        val u = url.trim().ifEmpty { "https://absen.smkn2malinau.sch.id/" }
        return if (u.endsWith("/")) u else "$u/"
    }

    /** Client ber-autentikasi device (X-Device-Id + X-Device-Api-Key). */
    fun create(
        deviceId: String,
        apiKey: String,
        baseUrl: String? = null,
        rateLimiter: RateLimiter = RateLimiter()
    ): ApiService {
        val client = OkHttpClient.Builder()
            .timeoutKiosk()
            .addInterceptor(DeviceAuthInterceptor(deviceId, apiKey, rateLimiter))
            .build()
        return retrofit(client, baseUrl)
    }

    /**
     * Client TANPA autentikasi device — dipakai hanya untuk registrasi
     * (`/auth/login/google` + `/device/register`), sebelum kita punya api key.
     */
    fun createForRegistration(baseUrl: String? = null, rateLimiter: RateLimiter = RateLimiter()): ApiService {
        val client = OkHttpClient.Builder()
            .timeoutKiosk()
            .addInterceptor { chain -> rateLimiter.acquire(); chain.proceed(chain.request()) }
            .build()
        return retrofit(client, baseUrl)
    }

    private fun retrofit(client: OkHttpClient, baseUrl: String?): ApiService =
        Retrofit.Builder()
            .baseUrl(baseUrl?.let { normalisasi(it) } ?: BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
}
