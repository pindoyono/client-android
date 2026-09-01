package com.smkn2malinau.absensi.audit

import android.util.Log
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.LivenessLogEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Liveness logger — tulis ke liveness_log TIAP kali liveness check dijalankan.
 * Termasuk yang gagal, dengan skor & ambang batas saat itu.
 * PRD bagian 9.4 — mencegah blind spot seperti bug Windows.
 */
class LivenessLogger(private val db: AbsensiDatabase, private val deviceId: String) {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    suspend fun log(
        frameId: String,
        wajahTerdeteksi: Boolean,
        isReal: Boolean,
        livenessScore: Double,
        ambangSaatItu: Double,
        alasanGagal: String? = null,
        siswaId: Long? = null
    ) {
        val now = LocalDateTime.now().format(fmt)
        try {
            db.logDao().insertLiveness(
                LivenessLogEntity(
                    timestamp = now,
                    frameId = frameId,
                    wajahTerdeteksi = wajahTerdeteksi,
                    isReal = isReal,
                    livenessScore = livenessScore,
                    ambangSaatItu = ambangSaatItu,
                    alasanGagal = alasanGagal,
                    siswaId = siswaId,
                    deviceId = deviceId,
                    createdAt = now
                )
            )
        } catch (e: Exception) {
            Log.e("LivenessLogger", "Failed to write liveness log", e)
        }
    }
}
