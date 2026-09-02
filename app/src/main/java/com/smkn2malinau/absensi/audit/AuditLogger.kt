package com.smkn2malinau.absensi.audit

import android.util.Log
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.DeviceAuditLog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Audit logger — tulis ke device_audit_log tiap kejadian relevan.
 * PRD bagian 9.4.
 */
class AuditLogger(private val db: AbsensiDatabase, private val deviceId: String) {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    suspend fun log(
        eventType: String,
        actor: String,
        action: String,
        details: String = "",
        status: String = "success",
        errorMessage: String? = null
    ) {
        val now = LocalDateTime.now().format(fmt)
        try {
            db.logDao().insertAudit(
                DeviceAuditLog(
                    timestamp = now,
                    event_type = eventType,
                    actor = actor,
                    action = action,
                    details = details,
                    status = status,
                    error_message = errorMessage,
                    device_id = deviceId,
                    created_at = now
                )
            )
        } catch (e: Exception) {
            Log.e("AuditLogger", "Failed to write audit log", e)
        }
    }

    suspend fun logLogin(actor: String, success: Boolean) {
        log("LOGIN", actor, "login", status = if (success) "success" else "failed")
    }

    suspend fun logEnrollment(siswaId: Int, success: Boolean) {
        log("ENROLLMENT", "admin", "enroll_siswa", details = "siswa_id=$siswaId",
            status = if (success) "success" else "failed")
    }

    suspend fun logSyncStart() {
        log("SYNC", "system", "sync_start")
    }

    suspend fun logSyncComplete(success: Boolean, message: String = "") {
        log("SYNC", "system", "sync_complete", details = message,
            status = if (success) "success" else "failed")
    }
}
