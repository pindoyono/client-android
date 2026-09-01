package com.smkn2malinau.absensi.security

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Port logika rate limiter dari api/client.py Windows (PRD bagian 9.6).
 * Membatasi laju request keluar dari device.
 */
class RateLimiter(private val minIntervalMs: Long = 500L) {
    private val lastRequestTime = AtomicLong(0)

    fun acquire() {
        val now = SystemClock.elapsedRealtime()
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
        lastRequestTime.set(SystemClock.elapsedRealtime())
    }
}
