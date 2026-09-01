package com.smkn2malinau.absensi.data.local.dao

import androidx.room.*
import com.smkn2malinau.absensi.data.local.entity.*

@Dao
interface AbsensiDao {
    @Query("SELECT * FROM absensi_lokal WHERE tanggal = :tanggal AND siswa_id = :siswaId")
    suspend fun getRecordsHariIni(siswaId: Long, tanggal: String): List<AbsensiLokalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAbsensi(absensi: AbsensiLokalEntity)

    @Query("SELECT * FROM absensi_lokal WHERE synced = 0")
    suspend fun getUnsyncedRecords(): List<AbsensiLokalEntity>

    @Update
    suspend fun updateAbsensi(absensi: AbsensiLokalEntity)
}

@Dao
interface SiswaDao {
    @Query("SELECT * FROM siswa_cache WHERE siswaId = :siswaId")
    suspend fun getSiswa(siswaId: Long): SiswaCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiswa(siswa: SiswaCacheEntity)

    @Query("DELETE FROM siswa_cache WHERE siswaId = :siswaId")
    suspend fun deleteSiswa(siswaId: Long)
}

@Dao
interface JadwalDao {
    @Query("SELECT * FROM jadwal_cache WHERE tanggal = :tanggal AND (kelas = :kelas OR kelas IS NULL)")
    suspend fun getJadwalServer(tanggal: String, kelas: String): List<JadwalCacheEntity>

    @Query("SELECT * FROM jadwal_override_lokal WHERE tanggal = :tanggal AND (kelas = :kelas OR kelas IS NULL)")
    suspend fun getJadwalOverrideLokal(tanggal: String, kelas: String): List<JadwalOverrideLokalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrideLokal(override: JadwalOverrideLokalEntity)

    @Query("SELECT * FROM jadwal_override_lokal WHERE terkirim = 0")
    suspend fun getUnsyncedOverrides(): List<JadwalOverrideLokalEntity>

    @Update
    suspend fun updateOverrideLokal(override: JadwalOverrideLokalEntity)
}

@Dao
interface DispensasiDao {
    @Query("SELECT * FROM dispensasi_cache WHERE siswaId = :siswaId AND tanggal = :tanggal")
    suspend fun getDispensasiAktif(siswaId: Long, tanggal: String): DispensasiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispensasi(dispensasi: DispensasiCacheEntity)
}

@Dao
interface LogDao {
    @Insert
    suspend fun insertAudit(log: DeviceAuditLogEntity)

    @Insert
    suspend fun insertLiveness(log: LivenessLogEntity)

    @Insert
    suspend fun insertSyncEvent(log: SyncEventLogEntity)
}
