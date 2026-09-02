package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jadwal_cache", primaryKeys = ["kelas", "tanggal"])
data class JadwalCache(
    val kelas: String,
    val tanggal: String,
    val hari: String,
    val jam_masuk: String,
    val jam_pulang: String,
    val sumber: String,
    val ditarik_pada: String
)
