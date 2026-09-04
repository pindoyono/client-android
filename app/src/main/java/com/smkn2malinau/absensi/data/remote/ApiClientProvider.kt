package com.smkn2malinau.absensi.data.remote

import com.google.gson.GsonBuilder
import com.smkn2malinau.absensi.BuildConfig
import com.smkn2malinau.absensi.security.RateLimiter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClientProvider {
    /** Base URL default dari BuildConfig (diisi lewat local.properties SERVER_BASE_URL=). */
    val BASE_URL: String = normalisasi(BuildConfig.SERVER_BASE_URL.ifBlank { "https://absen.smkn2malinau.sch.id/" })

    private val gson = GsonBuilder().setLenient().create()

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
