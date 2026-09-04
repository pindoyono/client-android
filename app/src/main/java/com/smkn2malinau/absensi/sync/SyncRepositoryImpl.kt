package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.remote.RosterItemDto
import java.time.Duration
import java.time.LocalDateTime

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
    override suspend fun hapusEnrollLokalTertimpa() {
        db.siswaDao().hapusEmbeddingEnrollLokalTertimpa()
        db.siswaDao().hapusSiswaEnrollLokalTertimpa()
    }
    override suspend fun insertDispensasi(dispensasi: DispensasiCache) = db.jadwalDao().insertDispensasi(listOf(dispensasi))
    override suspend fun gantiJadwalCache(jadwal: List<JadwalCache>) {
        db.jadwalDao().hapusSemuaJadwalCache()
        db.jadwalDao().insertJadwal(jadwal)
    }
    override suspend fun daftarKelas(): List<String> =
        db.siswaDao().getSemuaSiswa().map { it.kelas }.filter { it.isNotBlank() }.distinct()
    override suspend fun getUnsyncedOverrides(): List<JadwalOverrideLokal> = db.jadwalDao().getAntrianSyncOverride()
    override suspend fun updateOverrideLokal(override: JadwalOverrideLokal) = db.jadwalDao().updateOverrideLokal(override)
    override suspend fun insertSyncEvent(log: SyncEventLog) = db.logDao().insertSyncEvent(log)
    override suspend fun insertLiveness(log: LivenessLog) = db.logDao().insertLiveness(log)

    override suspend fun seedAkunRoster(guru: List<RosterItemDto>) {
        val now = LocalDateTime.now().toString()
        for (g in guru) {
            val id = g.email.trim().lowercase()
            if (id.isBlank()) continue
            val lama = db.akunDao().getByIdentitasApaPun(id)
            db.akunDao().upsert(
                AkunLokal(
                    identitas = id,
                    nama = g.nama.ifBlank { id },
                    role = g.role,
                    password_hash = lama?.password_hash,   // pertahankan password offline yg sudah di-set
                    salt = lama?.salt,
                    siswa_id = lama?.siswa_id,
                    aktif = if (g.aktif) 1 else 0,
                    diperbarui_pada = now,
                )
            )
        }
    }

    override suspend fun kesehatanCache(): KesehatanCache {
        val now = LocalDateTime.now()
        val jadwalT = db.jadwalDao().jadwalCacheTerbaru()?.let(::parseWaktu)
        val wajahT = db.siswaDao().embeddingTerbaru()?.let(::parseWaktu)
        return KesehatanCache(
            jadwalJamLalu = jadwalT?.let { Duration.between(it, now).toMinutes() / 60.0 },
            wajahHariLalu = wajahT?.let { Duration.between(it, now).toDays().toInt() },
            pendingKirim = db.absensiDao().countMenunggu() + db.absensiDao().countGagal(),
        )
    }

    private fun parseWaktu(raw: String): LocalDateTime? =
        try { LocalDateTime.parse(raw.trim()) } catch (e: Exception) { null }
}
