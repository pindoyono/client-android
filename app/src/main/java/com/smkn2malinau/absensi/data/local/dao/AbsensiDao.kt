package com.smkn2malinau.absensi.data.local.dao

import androidx.room.*
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal

@Dao
interface AbsensiDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAbsensi(absensi: AbsensiLokal)

    @Update
    suspend fun updateAbsensi(absensi: AbsensiLokal)

    @Query("SELECT * FROM absensi_lokal WHERE siswa_id = :siswaId AND tanggal = :tanggal")
    suspend fun getAbsensiHariIni(siswaId: Int, tanggal: String): List<AbsensiLokal>

    @Query("SELECT * FROM absensi_lokal WHERE synced = 0")
    suspend fun getAntrianSync(): List<AbsensiLokal>

    @Query("UPDATE absensi_lokal SET synced = 1, sync_status = 'ok' WHERE record_id = :recordId")
    suspend fun markSynced(recordId: String)

    // --- Statistik untuk Panel Admin (bagian Sinkronisasi) ---

    @Query("SELECT COUNT(*) FROM absensi_lokal")
    suspend fun countSemua(): Int

    @Query("SELECT COUNT(*) FROM absensi_lokal WHERE synced = 1")
    suspend fun countTersinkron(): Int

    @Query("SELECT COUNT(*) FROM absensi_lokal WHERE synced = 0 AND (sync_status IS NULL OR sync_status IN ('pending', ''))")
    suspend fun countMenunggu(): Int

    @Query("SELECT COUNT(*) FROM absensi_lokal WHERE synced = 0 AND sync_status = 'gagal'")
    suspend fun countGagal(): Int

    @Query("SELECT * FROM absensi_lokal ORDER BY dibuat_pada DESC LIMIT :limit")
    suspend fun recordTerbaru(limit: Int): List<AbsensiLokal>

    @Query("SELECT * FROM absensi_lokal WHERE siswa_id = :siswaId ORDER BY tanggal DESC, jam_aktual DESC LIMIT :limit")
    suspend fun recordSiswa(siswaId: Int, limit: Int): List<AbsensiLokal>
}
