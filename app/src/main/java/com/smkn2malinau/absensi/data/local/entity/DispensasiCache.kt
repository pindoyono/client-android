package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity

@Entity(tableName = "dispensasi_cache", primaryKeys = ["siswa_id", "tanggal", "jenis"])
data class DispensasiCache(
    val siswa_id: Int,
    val tanggal: String,
    val jenis: String,
    val kategori: String,
    val alasan: String
)
