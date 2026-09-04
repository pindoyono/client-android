package com.smkn2malinau.absensi.data.local.dao

import androidx.room.*
import com.smkn2malinau.absensi.data.local.entity.JadwalCache
import com.smkn2malinau.absensi.data.local.entity.JadwalOverrideLokal
import com.smkn2malinau.absensi.data.local.entity.DispensasiCache

@Dao
interface JadwalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJadwal(jadwal: List<JadwalCache>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispensasi(dispensasi: List<DispensasiCache>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: JadwalOverrideLokal)

    @Update
    suspend fun updateOverrideLokal(override: JadwalOverrideLokal)

    @Query("SELECT * FROM jadwal_cache WHERE kelas = :kelas AND tanggal = :tanggal")
    suspend fun getJadwal(kelas: String, tanggal: String): JadwalCache?

    @Query("SELECT * FROM jadwal_override_lokal WHERE tanggal = :tanggal AND (kelas = :kelas OR kelas IS NULL) ORDER BY dibuat_pada DESC LIMIT 1")
    suspend fun getOverrideTerbaru(kelas: String, tanggal: String): JadwalOverrideLokal?

    @Query("SELECT * FROM jadwal_override_lokal WHERE terkirim = 0")
    suspend fun getAntrianSyncOverride(): List<JadwalOverrideLokal>

    // --- Panel Admin: kelola jadwal ---

    @Query("SELECT * FROM jadwal_override_lokal ORDER BY dibuat_pada DESC")
    suspend fun getSemuaOverrideLokal(): List<JadwalOverrideLokal>

    @Query("DELETE FROM jadwal_override_lokal WHERE id = :id")
    suspend fun deleteOverrideLokal(id: String)

    @Query("SELECT * FROM jadwal_cache ORDER BY kelas, tanggal")
    suspend fun getSemuaJadwalCache(): List<JadwalCache>

    @Query("SELECT COUNT(*) FROM jadwal_cache")
    suspend fun countJadwalCache(): Int

    /** Timestamp jadwal paling baru ditarik dari server (untuk badge kesegaran kiosk). */
    @Query("SELECT MAX(ditarik_pada) FROM jadwal_cache")
    suspend fun jadwalCacheTerbaru(): String?

    /**
     * Jadwal kelas mana pun untuk tanggal tertentu — untuk header kiosk saat idle
     * (setara `jadwal_pertama_tersedia` di client Windows). Dibatasi ke tanggal
     * yang diminta supaya baris hari lalu tidak ikut tampil. Utamakan override.
     */
    @Query("SELECT * FROM jadwal_cache WHERE tanggal = :tanggal ORDER BY (sumber = 'override') DESC, kelas LIMIT 1")
    suspend fun getJadwalHariIni(tanggal: String): JadwalCache?

    /** Ganti seluruh cache jadwal (setara `replace_jadwal_cache` di client Windows). */
    @Query("DELETE FROM jadwal_cache")
    suspend fun hapusSemuaJadwalCache()

    @Query("SELECT COUNT(*) FROM dispensasi_cache")
    suspend fun countDispensasiCache(): Int

    @Query(
        "UPDATE jadwal_override_lokal SET status_push = 'pending', terkirim = 0, pesan_push = NULL " +
            "WHERE status_push = 'ditolak'"
    )
    suspend fun resetOverrideDitolak(): Int

    @Query("SELECT * FROM dispensasi_cache WHERE siswa_id = :siswaId AND tanggal = :tanggal AND jenis = :jenis")
    suspend fun getDispensasiAktif(siswaId: Int, tanggal: String, jenis: String): DispensasiCache?

    @Query("SELECT * FROM dispensasi_cache WHERE siswa_id = :siswaId AND tanggal = :tanggal")
    suspend fun getDispensasiHariIni(siswaId: Int, tanggal: String): List<DispensasiCache>
}
