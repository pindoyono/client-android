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

    /** N absensi terakhir (kiosk) — nama + jenis (masuk/pulang) + keterangan (izin/sakit/dll). */
    @Query(
        "SELECT s.nama AS nama, a.type AS type, " +
            "a.status_kehadiran_otomatis AS status_kehadiran_otomatis, a.catatan AS catatan " +
            "FROM absensi_lokal a JOIN siswa_cache s ON s.siswa_id = a.siswa_id " +
            "ORDER BY a.dibuat_pada DESC LIMIT :limit"
    )
    suspend fun riwayatAbsenTerbaru(limit: Int): List<RiwayatAbsenRow>
}

/** Satu baris riwayat absen terbaru — dipakai daftar 5 absensi terakhir di kiosk. */
data class RiwayatAbsenRow(
    val nama: String,
    val type: String, // MASUK | PULANG
    val status_kehadiran_otomatis: String,
    val catatan: String,
)
