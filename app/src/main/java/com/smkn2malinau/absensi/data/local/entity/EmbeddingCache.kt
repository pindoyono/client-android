package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "embedding_cache")
data class EmbeddingCache(
    @PrimaryKey val siswa_id: Int,
    val embedding_encrypted: ByteArray,
    val model_version: String,
    val diperbarui_pada: String
)
