package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_event_log")
data class SyncEventLog(
    @PrimaryKey(autoGenerate = true) val log_id: Int = 0,
    val timestamp: String,
    val duration_ms: Long,
    val status: String,
    val batch_count: Int,
    val success_count: Int,
    val duplicate_count: Int,
    val fail_count: Int,
    val error_message: String?,
    val device_id: String,
    val created_at: String
)
