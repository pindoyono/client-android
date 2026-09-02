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
}
