package com.smkn2malinau.absensi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.smkn2malinau.absensi.data.local.entity.DeviceAuditLog
import com.smkn2malinau.absensi.data.local.entity.LivenessLog
import com.smkn2malinau.absensi.data.local.entity.SyncEventLog

@Dao
interface LogDao {
    @Insert
    suspend fun insertAudit(log: DeviceAuditLog)

    @Insert
    suspend fun insertLiveness(log: LivenessLog)

    @Insert
    suspend fun insertSyncEvent(log: SyncEventLog)
}
