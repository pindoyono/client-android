package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.*

/**
 * Concrete implementation of SyncRepository using Room database.
 */
class SyncRepositoryImpl(private val db: AbsensiDatabase) : SyncRepository {
    override suspend fun getUnsyncedRecords(): List<AbsensiLokal> = db.absensiDao().getAntrianSync()
    override suspend fun updateAbsensi(absensi: AbsensiLokal) = db.absensiDao().updateAbsensi(absensi)
    override suspend fun insertSiswa(siswa: SiswaCache) = db.siswaDao().insertSiswa(listOf(siswa))
    override suspend fun deleteSiswa(siswaId: Int) = db.siswaDao().deleteSiswa(siswaId)
    override suspend fun insertEmbedding(embedding: EmbeddingCache) = db.siswaDao().insertEmbedding(listOf(embedding))
    override suspend fun deleteEmbedding(siswaId: Int) = db.siswaDao().deleteEmbedding(siswaId)
    override suspend fun insertDispensasi(dispensasi: DispensasiCache) = db.jadwalDao().insertDispensasi(listOf(dispensasi))
    override suspend fun insertJadwal(jadwal: JadwalCache) = db.jadwalDao().insertJadwal(listOf(jadwal))
    override suspend fun getUnsyncedOverrides(): List<JadwalOverrideLokal> = db.jadwalDao().getAntrianSyncOverride()
    override suspend fun updateOverrideLokal(override: JadwalOverrideLokal) = db.jadwalDao().updateOverrideLokal(override)
    override suspend fun insertSyncEvent(log: SyncEventLog) = db.logDao().insertSyncEvent(log)
    override suspend fun insertLiveness(log: LivenessLog) = db.logDao().insertLiveness(log)
}
