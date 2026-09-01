package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.*

/**
 * Concrete implementation of SyncRepository using Room database.
 */
class SyncRepositoryImpl(private val db: AbsensiDatabase) : SyncRepository {
    override suspend fun getUnsyncedRecords(): List<AbsensiLokalEntity> = db.absensiDao().getUnsyncedRecords()
    override suspend fun updateAbsensi(absensi: AbsensiLokalEntity) = db.absensiDao().updateAbsensi(absensi)
    override suspend fun insertSiswa(siswa: SiswaCacheEntity) = db.siswaDao().insertSiswa(siswa)
    override suspend fun deleteSiswa(siswaId: Long) = db.siswaDao().deleteSiswa(siswaId)
    override suspend fun insertDispensasi(dispensasi: DispensasiCacheEntity) = db.dispensasiDao().insertDispensasi(dispensasi)
    override suspend fun getUnsyncedOverrides(): List<JadwalOverrideLokalEntity> = db.jadwalDao().getUnsyncedOverrides()
    override suspend fun updateOverrideLokal(override: JadwalOverrideLokalEntity) = db.jadwalDao().updateOverrideLokal(override)
    override suspend fun insertSyncEvent(log: SyncEventLogEntity) = db.logDao().insertSyncEvent(log)
    override suspend fun insertLiveness(log: LivenessLogEntity) = db.logDao().insertLiveness(log)
}
