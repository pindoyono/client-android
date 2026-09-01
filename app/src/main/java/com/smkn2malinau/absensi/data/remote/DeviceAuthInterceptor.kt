package com.smkn2malinau.absensi.data.remote

import com.smkn2malinau.absensi.security.RateLimiter
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor menambahkan header X-Device-Id dan X-Device-Api-Key pada setiap request.
 * Tidak ada refresh token, JWT, atau HMAC – sesuai PRD bagian 4.
 */
class DeviceAuthInterceptor(
    private val deviceId: String,
    private val apiKey: String,
    private val rateLimiter: RateLimiter = RateLimiter()
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Rate limiting sebelum request
        rateLimiter.acquire()
        val original = chain.request()
        val request = original.newBuilder()
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Device-Api-Key", apiKey)
            .build()
        return chain.proceed(request)
    }
}
