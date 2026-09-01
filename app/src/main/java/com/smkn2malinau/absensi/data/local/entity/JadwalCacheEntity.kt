package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jadwal_cache",
    indices = [Index(value = ["kelas", "tanggal"])]
)
data class JadwalCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kelas: String,
    val tanggal: String,
    val hari: String,
    val jamMasuk: String,
    val jamPulang: String,
    val sumber: String,
    val ditarikPada: String
)
