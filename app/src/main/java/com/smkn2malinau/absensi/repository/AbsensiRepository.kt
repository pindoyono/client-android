package com.smkn2malinau.absensi.repository

import android.util.Log
import com.smkn2malinau.absensi.business.AttendanceLogic
import com.smkn2malinau.absensi.business.HasilAbsen
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.data.local.dao.RiwayatAbsenRow
import com.smkn2malinau.absensi.data.local.entity.DispensasiCache
import com.smkn2malinau.absensi.face.CryptoEmbedding
import com.smkn2malinau.absensi.face.LivenessEvaluator
import java.time.Duration
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
        catatan: String?,
        /** true = status geofencing terakhir menandai lokasi mock (fake GPS)
         *  saat record ini dibuat. Disimpan & dikirim ke server apa adanya. */
        lokasiMock: Boolean = false,
    ): Boolean

    /**
     * Ringkasan untuk status bar kiosk — setara header kiosk Windows
     * (`Sync: dd/MM HH:mm · N antre, N wajah, N jadwal`, badge kesegaran, jam masuk/pulang).
     */
    suspend fun ringkasanKiosk(tanggal: String = LocalDate.now().toString()): RingkasanKiosk

    /** [limit] absensi terakhir (paling baru duluan) — daftar riwayat kiosk. */
    suspend fun riwayatAbsenTerbaru(limit: Int = 5): List<RiwayatAbsenRow>
}

/** Data header kiosk (PRD observabilitas degradasi + PRD bagian 4.4). */
data class RingkasanKiosk(
    /** Sync sukses terakhir; null = belum pernah. */
    val syncTerakhir: LocalDateTime? = null,
    /**
     * Status siklus sync PALING AKHIR (sukses/gagal), lepas dari jaringan.
     * Dipakai untuk menentukan pil "Online · tersinkron" vs "Online · belum tersinkron"
     * — setara `cek_koneksi()` di awal siklus sync client Windows.
     */
    val sinkronTerakhirSukses: Boolean = false,
    /** Sudah pernah menjalankan minimal satu siklus sync (sukses atau gagal). */
    val pernahSinkron: Boolean = false,
    /** Absensi lokal yang masih menunggu / gagal dikirim ke server. */
    val antreKirim: Int = 0,
    /** Jumlah wajah (embedding) di cache lokal. */
    val jumlahWajah: Int = 0,
    /** Jumlah baris jadwal di cache lokal. */
    val jumlahJadwal: Int = 0,
    /** Jam masuk/pulang untuk header — jadwal umum hari ini, fallback jadwal pertama tersedia. */
    val jadwalHariIni: AttendanceLogic.JadwalEfektif? = null,
    val kesegaran: KesegaranData = KesegaranData(),
)

/**
 * Status kesegaran cache lokal. `diketahui = false` saat belum pernah sync
 * (badge disembunyikan, bukan ditampilkan merah).
 */
data class KesegaranData(
    val diketahui: Boolean = false,
    /** Nama data yang sudah basi, mis. ["Jadwal", "Wajah"]. */
    val dataBasi: List<String> = emptyList(),
) {
    val segar: Boolean get() = diketahui && dataBasi.isEmpty()
}

data class SiswaCocok(
    val ditemukan: Boolean,
    val siswaId: Int = -1,
    val nis: String = "",
    val nama: String = "",
    val kelas: String = "",
    val jarak: Float = Float.MAX_VALUE,
    /** Jumlah embedding yang berhasil didekripsi & dibandingkan (0 = key salah / cache kosong). */
    val jumlahDibandingkan: Int = 0,
)

class AbsensiRepositoryImpl(
    private val db: AbsensiDatabase,
    private val deviceId: String,
    /** Fernet key embedding (`FACE_ENCRYPTION_KEY` server). Kosong → semua embedding server gagal didekripsi. */
    private val faceKey: String = "",
) : AbsensiRepository {

    private val jamFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val jamPendekFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Cache embedding yang sudah didekripsi (kunci = siswa_id), supaya frame kiosk berikutnya
     * tidak mendekripsi ulang seluruh `embedding_cache` (Fernet: AES-CBC + HMAC per baris) —
     * biaya itu O(n) per scan dan makin berat seiring jumlah siswa terenroll. Entry dianggap
     * valid selama ciphertext-nya (`encrypted`) masih sama dengan baris di DB; kalau beda
     * (re-enroll/sync baru) baris itu saja yang didekripsi ulang, sisanya tetap dari cache.
     */
    private class EmbeddingTercache(val encrypted: ByteArray, val vektor: FloatArray)
    @Volatile private var cacheEmbedding: Map<Int, EmbeddingTercache> = emptyMap()

    override suspend fun cariSiswaCocok(embedding: FloatArray, ambangJarak: Float): SiswaCocok {
        if (embedding.isEmpty()) return SiswaCocok(ditemukan = false)

        val siswaById = db.siswaDao().getSemuaSiswa().associateBy { it.siswa_id }
        val cacheLama = cacheEmbedding
        val cacheBaru = HashMap<Int, EmbeddingTercache>(cacheLama.size)
        var terbaik: SiswaCocok = SiswaCocok(ditemukan = false)
        var gagalDekripsi = 0
        var dibandingkan = 0

        for (row in db.siswaDao().getSemuaEmbedding()) {
            val darCache = cacheLama[row.siswa_id]?.takeIf { it.encrypted.contentEquals(row.embedding_encrypted) }
            val kandidat = if (darCache != null) {
                darCache.vektor
            } else {
                try {
                    CryptoEmbedding.decryptEmbedding(row.embedding_encrypted, faceKey)
                } catch (e: Exception) {
                    gagalDekripsi++
                    continue
                }
            }
            cacheBaru[row.siswa_id] = EmbeddingTercache(row.embedding_encrypted, kandidat)
            if (kandidat.size != embedding.size) continue
            dibandingkan++

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
        cacheEmbedding = cacheBaru
        if (gagalDekripsi > 0) {
            Log.w(
                "AbsensiRepository",
                "$gagalDekripsi embedding gagal didekripsi — cek FACE_ENCRYPTION_KEY device vs server",
            )
        }
        return if (terbaik.jarak < ambangJarak) terbaik.copy(ditemukan = true, jumlahDibandingkan = dibandingkan)
        else SiswaCocok(ditemukan = false, jarak = terbaik.jarak, jumlahDibandingkan = dibandingkan)
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
        catatan: String?,
        lokasiMock: Boolean,
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
            lokasi_mock = if (lokasiMock) 1 else 0,
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

    override suspend fun ringkasanKiosk(tanggal: String): RingkasanKiosk {
        val syncTerakhir = db.logDao().syncSuksesTerakhir()?.let(::parseWaktu)
        val eventTerakhir = db.logDao().sinkronTerakhir()
        val antre = db.absensiDao().countMenunggu() + db.absensiDao().countGagal()
        val wajah = db.siswaDao().countEmbedding()
        val jadwalCount = db.jadwalDao().countJadwalCache()
        return RingkasanKiosk(
            syncTerakhir = syncTerakhir,
            sinkronTerakhirSukses = eventTerakhir?.status == "success",
            pernahSinkron = eventTerakhir != null,
            antreKirim = antre,
            jumlahWajah = wajah,
            jumlahJadwal = jadwalCount,
            jadwalHariIni = jadwalHeaderHariIni(tanggal),
            kesegaran = hitungKesegaran(),
        )
    }

    override suspend fun riwayatAbsenTerbaru(limit: Int): List<RiwayatAbsenRow> = db.absensiDao().riwayatAbsenTerbaru(limit)

    /** Jadwal umum hari ini; bila tak ada, jadwal kelas mana pun untuk HARI INI. */
    private suspend fun jadwalHeaderHariIni(tanggal: String): AttendanceLogic.JadwalEfektif? {
        jadwalEfektif("", tanggal)?.let { return it }
        val row = db.jadwalDao().getJadwalHariIni(tanggal) ?: return null
        val masuk = parseJam(row.jam_masuk) ?: return null
        val pulang = parseJam(row.jam_pulang) ?: return null
        return AttendanceLogic.JadwalEfektif(masuk, pulang)
    }

    private suspend fun hitungKesegaran(): KesegaranData {
        val now = LocalDateTime.now()
        val jadwalT = db.jadwalDao().jadwalCacheTerbaru()?.let(::parseWaktu)
        val wajahT = db.siswaDao().embeddingTerbaru()?.let(::parseWaktu)
        if (jadwalT == null && wajahT == null) return KesegaranData(diketahui = false)

        val basi = buildList {
            if (jadwalT == null || Duration.between(jadwalT, now).toHours() > BATAS_STALE_JADWAL_JAM) add("Jadwal")
            if (wajahT == null || Duration.between(wajahT, now).toDays() > BATAS_STALE_WAJAH_HARI) add("Wajah")
        }
        return KesegaranData(diketahui = true, dataBasi = basi)
    }

    private fun parseWaktu(raw: String): LocalDateTime? =
        try {
            LocalDateTime.parse(raw.trim())
        } catch (e: Exception) {
            null
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

    private companion object {
        /** Ambang cache basi — setara `BATAS_STALE_*` di config client Windows. */
        const val BATAS_STALE_JADWAL_JAM = 6L
        const val BATAS_STALE_WAJAH_HARI = 3L
    }
}
