package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "siswa_cache")
data class SiswaCache(
    @PrimaryKey val siswa_id: Int,
    val nis: String,
    val nama: String,
    val kelas: String
)
