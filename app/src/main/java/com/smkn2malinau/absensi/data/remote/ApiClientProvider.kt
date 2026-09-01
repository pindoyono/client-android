package com.smkn2malinau.absensi.data.remote

import com.google.gson.GsonBuilder
import com.smkn2malinau.absensi.security.RateLimiter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClientProvider {
    private const val BASE_URL = "https://api.example.com/" // replace with real endpoint

    fun create(
        deviceId: String,
        apiKey: String,
        rateLimiter: RateLimiter = RateLimiter()
    ): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(DeviceAuthInterceptor(deviceId, apiKey, rateLimiter))
            .build()

        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
