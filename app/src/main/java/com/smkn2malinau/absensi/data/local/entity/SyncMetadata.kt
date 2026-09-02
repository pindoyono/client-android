package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val kunci: String,
    val nilai: String
)
