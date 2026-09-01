package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liveness_log")
data class LivenessLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val timestamp: String,
    val frameId: String,
    val wajahTerdeteksi: Boolean,
    val isReal: Boolean,
    val livenessScore: Double,
    val ambangSaatItu: Double,
    val alasanGagal: String?,
    val siswaId: Long?,
    val deviceId: String,
    val createdAt: String
)
