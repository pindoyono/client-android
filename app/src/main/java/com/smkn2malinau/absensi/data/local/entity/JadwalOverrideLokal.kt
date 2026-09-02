package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jadwal_override_lokal")
data class JadwalOverrideLokal(
    @PrimaryKey val id: String, // UUID
    val tanggal: String,
    val kelas: String?,
    val jam_masuk: String,
    val jam_pulang: String,
    val alasan: String?,
    val dibuat_pada: String,
    val terkirim: Int = 0,
    val status_push: String = "pending",
    val pesan_push: String? = null
)
