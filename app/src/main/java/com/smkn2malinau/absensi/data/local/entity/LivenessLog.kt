package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liveness_log")
data class LivenessLog(
    @PrimaryKey(autoGenerate = true) val log_id: Int = 0,
    val timestamp: String,
    val frame_id: String?,
    val wajah_terdeteksi: Int,
    val is_real: Int,
    val liveness_score: Double,
    val ambang_saat_itu: Double,
    val alasan_gagal: String?,
    val siswa_id: Int?,
    val device_id: String,
    val created_at: String
)
