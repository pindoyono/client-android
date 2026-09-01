package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dispensasi_cache")
data class DispensasiCacheEntity(
    @PrimaryKey val siswaId: Long,
    val tanggal: String,
    val jenis: String,
    val kategori: String,
    val alasan: String?
)
