package com.smkn2malinau.absensi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "embedding_cache")
data class EmbeddingCacheEntity(
    @PrimaryKey val siswaId: Long,
    @ColumnInfo(name = "embedding_encrypted") val embeddingEncrypted: ByteArray,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    @ColumnInfo(name = "diperbarui_pada") val diperbaruiPada: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingCacheEntity
        return siswaId == other.siswaId && modelVersion == other.modelVersion
    }

    override fun hashCode(): Int {
        var result = siswaId.hashCode()
        result = 31 * result + modelVersion.hashCode()
        return result
    }
}
