package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_event_log")
data class SyncEventLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val timestamp: String,
    val durationMs: Long,
    val status: String,
    val batchCount: Int,
    val successCount: Int,
    val duplicateCount: Int,
    val failCount: Int,
    val errorMessage: String?,
    val deviceId: String,
    val createdAt: String
)