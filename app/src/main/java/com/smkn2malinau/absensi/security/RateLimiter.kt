package com.smkn2malinau.absensi.security

import java.util.concurrent.atomic.AtomicLong

/**
 * Port logika rate limiter dari api/client.py Windows (PRD bagian 9.6).
 * Membatasi laju request keluar dari device.
 * Menggunakan System.currentTimeMillis() agar unit-testable di JVM.
 */
class RateLimiter(private val minIntervalMs: Long = 500L) {
    private val lastRequestTime = AtomicLong(0)

    fun acquire() {
        val now = System.currentTimeMillis()
        val last = lastRequestTime.get()
        val diff = now - last
        if (diff < minIntervalMs) {
            val sleepTime = minIntervalMs - diff
            try {
                Thread.sleep(sleepTime)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        lastRequestTime.set(System.currentTimeMillis())
    }
}
