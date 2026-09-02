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

    @Query("SELECT * FROM dispensasi_cache WHERE siswa_id = :siswaId AND tanggal = :tanggal AND jenis = :jenis")
    suspend fun getDispensasiAktif(siswaId: Int, tanggal: String, jenis: String): DispensasiCache?

    @Query("SELECT * FROM dispensasi_cache WHERE siswa_id = :siswaId AND tanggal = :tanggal")
    suspend fun getDispensasiHariIni(siswaId: Int, tanggal: String): List<DispensasiCache>
}
