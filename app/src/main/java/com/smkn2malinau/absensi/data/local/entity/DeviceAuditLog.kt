package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_audit_log")
data class DeviceAuditLog(
    @PrimaryKey(autoGenerate = true) val log_id: Int = 0,
    val timestamp: String,
    val event_type: String,
    val actor: String,
    val action: String,
    val details: String,
    val status: String,
    val error_message: String?,
    val device_id: String,
    val created_at: String
)
