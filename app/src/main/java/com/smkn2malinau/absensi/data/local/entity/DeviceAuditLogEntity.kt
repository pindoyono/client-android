package com.smkn2malinau.absensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_audit_log")
data class DeviceAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val timestamp: String,
    val eventType: String,
    val actor: String,
    val action: String,
    val details: String?,
    val status: String,
    val errorMessage: String?,
    val deviceId: String,
    val createdAt: String
)
