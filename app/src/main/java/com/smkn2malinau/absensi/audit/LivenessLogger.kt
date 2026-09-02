package com.smkn2malinau.absensi.audit

import android.util.Log
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.LivenessLog
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
        siswaId: Int? = null
    ) {
        val now = LocalDateTime.now().format(fmt)
        try {
            db.logDao().insertLiveness(
                LivenessLog(
                    timestamp = now,
                    frame_id = frameId,
                    wajah_terdeteksi = if (wajahTerdeteksi) 1 else 0,
                    is_real = if (isReal) 1 else 0,
                    liveness_score = livenessScore,
                    ambang_saat_itu = ambangSaatItu,
                    alasan_gagal = alasanGagal,
                    siswa_id = siswaId,
                    device_id = deviceId,
                    created_at = now
                )
            )
        } catch (e: Exception) {
            Log.e("LivenessLogger", "Failed to write liveness log", e)
        }
    }
}
