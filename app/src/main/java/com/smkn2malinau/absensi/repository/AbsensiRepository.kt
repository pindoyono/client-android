package com.smkn2malinau.absensi.repository

import android.util.Log
import com.smkn2malinau.absensi.business.AttendanceLogic
import com.smkn2malinau.absensi.business.HasilAbsen
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.data.local.entity.DispensasiCache
import com.smkn2malinau.absensi.face.CryptoEmbedding
import com.smkn2malinau.absensi.face.LivenessEvaluator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Lapisan domain yang menyambungkan business logic ke Room DAO (PRD bagian 4).
 * Semua fungsi yang dibutuhkan KioskViewModel ada di sini — bukan tersebar.
 */
interface AbsensiRepository {

    /** Cari siswa yang embedding-nya paling cocok. Pakai definisi *distance* (PRD bagian 3). */
    suspend fun cariSiswaCocok(
        embedding: FloatArray,
        ambangJarak: Float = LivenessEvaluator.AMBANG_JARAK_DEFAULT
    ): SiswaCocok

    /** Jadwal efektif: override lokal > jadwal cache server > null. */
    suspend fun jadwalEfektif(kelas: String, tanggal: String = LocalDate.now().toString()): AttendanceLogic.JadwalEfektif?

    /** Status absensi siswa hari ini (sudah masuk / sudah pulang). */
    suspend fun statusHariIni(siswaId: Int, tanggal: String = LocalDate.now().toString()): AttendanceLogic.StatusAbsensi

    /** Dispensasi pulang-cepat aktif untuk siswa hari ini (null bila tidak ada). */
    suspend fun dispensasiAktif(siswaId: Int, tanggal: String = LocalDate.now().toString()): DispensasiCache?

    /**
     * Simpan absensi ke `absensi_lokal`.
     * @return true bila baris baru tersimpan, false bila ditolak constraint UNIQUE(siswa_id, tanggal, type).
     */
    suspend fun simpanAbsensi(
        siswaId: Int,
        hasil: HasilAbsen,
        statusKehadiranOtomatis: String,
        catatan: String?
    ): Boolean
}

data class SiswaCocok(
    val ditemukan: Boolean,
    val siswaId: Int = -1,
    val nis: String = "",
    val nama: String = "",
    val kelas: String = "",
    val jarak: Float = Float.MAX_VALUE
)

class AbsensiRepositoryImpl(
    private val db: AbsensiDatabase,
    private val deviceId: String
) : AbsensiRepository {

    private val jamFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val jamPendekFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun cariSiswaCocok(embedding: FloatArray, ambangJarak: Float): SiswaCocok {
        if (embedding.isEmpty()) return SiswaCocok(ditemukan = false)

        val siswaById = db.siswaDao().getSemuaSiswa().associateBy { it.siswa_id }
        var terbaik: SiswaCocok = SiswaCocok(ditemukan = false)

        for (row in db.siswaDao().getSemuaEmbedding()) {
            val kandidat = try {
                CryptoEmbedding.decryptEmbedding(row.embedding_encrypted)
            } catch (e: Exception) {
                Log.w("AbsensiRepository", "Gagal dekripsi embedding siswa ${row.siswa_id}", e)
                continue
            }
            if (kandidat.size != embedding.size) continue

            val jarak = LivenessEvaluator.jarakWajah(embedding, kandidat)
            if (jarak < terbaik.jarak) {
                val s = siswaById[row.siswa_id]
                terbaik = SiswaCocok(
                    ditemukan = jarak < ambangJarak,
                    siswaId = row.siswa_id,
                    nis = s?.nis ?: "",
                    nama = s?.nama ?: "",
                    kelas = s?.kelas ?: "",
                    jarak = jarak
                )
            }
        }
        return if (terbaik.jarak < ambangJarak) terbaik.copy(ditemukan = true) else SiswaCocok(ditemukan = false, jarak = terbaik.jarak)
    }

    override suspend fun jadwalEfektif(kelas: String, tanggal: String): AttendanceLogic.JadwalEfektif? {
        db.jadwalDao().getOverrideTerbaru(kelas, tanggal)?.let { ov ->
            val masuk = parseJam(ov.jam_masuk)
            val pulang = parseJam(ov.jam_pulang)
            if (masuk != null && pulang != null) return AttendanceLogic.JadwalEfektif(masuk, pulang)
        }
        db.jadwalDao().getJadwal(kelas, tanggal)?.let { j ->
            val masuk = parseJam(j.jam_masuk)
            val pulang = parseJam(j.jam_pulang)
            if (masuk != null && pulang != null) return AttendanceLogic.JadwalEfektif(masuk, pulang)
        }
        return null
    }

    override suspend fun statusHariIni(siswaId: Int, tanggal: String): AttendanceLogic.StatusAbsensi {
        val rows = db.absensiDao().getAbsensiHariIni(siswaId, tanggal)
        val masuk = rows.firstOrNull { it.type == "MASUK" }
        val pulang = rows.firstOrNull { it.type == "PULANG" }
        return AttendanceLogic.StatusAbsensi(
            sudahMasuk = masuk != null,
            sudahPulang = pulang != null,
            jamMasukAktual = masuk?.jam_aktual?.let { parseJam(it) },
            jamPulangAktual = pulang?.jam_aktual?.let { parseJam(it) }
        )
    }

    override suspend fun dispensasiAktif(siswaId: Int, tanggal: String): DispensasiCache? {
        val semua = db.jadwalDao().getDispensasiHariIni(siswaId, tanggal)
        if (semua.isEmpty()) return null
        // Utamakan yang jelas-jelas pulang cepat, jika tidak ada ambil yang pertama.
        return semua.firstOrNull { it.jenis.uppercase().let { j -> j.contains("PULANG") || j.contains("CEPAT") } }
            ?: semua.first()
    }

    override suspend fun simpanAbsensi(
        siswaId: Int,
        hasil: HasilAbsen,
        statusKehadiranOtomatis: String,
        catatan: String?
    ): Boolean {
        val type = when (hasil) {
            HasilAbsen.BERHASIL_MASUK_NORMAL, HasilAbsen.BERHASIL_MASUK_TERLAMBAT -> "MASUK"
            HasilAbsen.BERHASIL_PULANG_NORMAL, HasilAbsen.BERHASIL_PULANG_CEPAT -> "PULANG"
            else -> return false
        }
        val now = LocalDateTime.now()
        val record = AbsensiLokal(
            record_id = UUID.randomUUID().toString(),
            siswa_id = siswaId,
            tanggal = LocalDate.now().toString(),
            type = type,
            jam_aktual = now.toLocalTime().format(jamFmt),
            status_kehadiran_otomatis = statusKehadiranOtomatis,
            catatan = catatan ?: "",
            device_id = deviceId,
            dibuat_pada = now.toString()
        )
        return try {
            db.absensiDao().insertAbsensi(record)
            true
        } catch (e: Exception) {
            // SQLiteConstraintException — sudah ada baris (siswa_id, tanggal, type) yang sama.
            Log.w("AbsensiRepository", "simpanAbsensi ditolak constraint: ${e.message}")
            false
        }
    }

    private fun parseJam(raw: String): LocalTime? = try {
        val t = raw.trim()
        when {
            t.count { it == ':' } >= 2 -> LocalTime.parse(t, jamFmt)
            t.contains(':') -> LocalTime.parse(t, jamPendekFmt)
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
