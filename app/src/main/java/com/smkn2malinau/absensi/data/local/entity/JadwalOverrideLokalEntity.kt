package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jadwal_override_lokal")
data class JadwalOverrideLokalEntity(
    @PrimaryKey val id: String, // UUID, idempotency key ke server
    val tanggal: String,
    val kelas: String?,
    val jamMasuk: String,
    val jamPulang: String,
    val alasan: String?,
    val dibuatPada: String,
    val terkirim: Int = 0, // 0 = belum, 1 = sudah
    val statusPush: String = "pending", // "pending" | "ok" | "ditolak"
    val pesanPush: String?
)
