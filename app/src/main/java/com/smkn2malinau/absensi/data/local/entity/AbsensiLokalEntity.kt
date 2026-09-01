package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "absensi_lokal",
    indices = [
        Index(value = ["siswa_id", "tanggal", "type"], unique = true)
    ]
)
data class AbsensiLokalEntity(
    @PrimaryKey val recordId: String, // UUID dibuat di Android
    val siswaId: Long,
    val tanggal: String,
    val type: String, // "MASUK" | "PULANG"
    val jamAktual: String,
    val statusKehadiranOtomatis: String,
    val catatan: String?,
    val deviceId: String,
    val synced: Int = 0, // 0 = belum, 1 = sudah
    val syncStatus: String?,
    val percobaanSync: Int = 0,
    val dibuatPada: String
)
