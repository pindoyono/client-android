package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "siswa_cache")
data class SiswaCacheEntity(
    @PrimaryKey val siswaId: Long,
    val nis: String,
    val nama: String,
    val kelas: String
)
