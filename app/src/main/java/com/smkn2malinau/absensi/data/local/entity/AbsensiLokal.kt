package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "absensi_lokal",
    indices = [Index(value = ["siswa_id", "tanggal", "type"], unique = true)]
)
data class AbsensiLokal(
    @PrimaryKey val record_id: String, // UUID
    val siswa_id: Int,
    val tanggal: String,
    val type: String, // MASUK | PULANG
    val jam_aktual: String,
    val status_kehadiran_otomatis: String,
    val catatan: String,
    val device_id: String,
    val synced: Int = 0,
    val sync_status: String = "pending",
    val percobaan_sync: Int = 0,
    val dibuat_pada: String
)
