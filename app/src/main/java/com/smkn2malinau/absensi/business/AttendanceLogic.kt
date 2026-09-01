package com.smkn2malinau.absensi.business

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Port persis state machine dari PRD bagian 3.1–3.4.
 * 100% unit-testable tanpa emulator — tidak ada Android dependency.
 */
object AttendanceLogic {

    const val BATAS_AWAL_MASUK_JAM = 2L
    const val BATAS_TOLERANSI_MENIT = 5L

    private val fmtJam = DateTimeFormatter.ofPattern("HH:mm")
    private val fmtTanggal = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val fmtDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /**
     * Menghasilkan record_id UUID untuk absensi lokal.
     */
    fun generateRecordId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    /**
     * Menentukan status absensi harian dari record yang sudah ada.
     */
    fun tentukanStatusAbsenHarian(records: List<RecordAbsen>): StatusAbsen {
        if (records.isEmpty()) return StatusAbsen.BELUM_ABSEN

        val hasMasuk = records.any { it.type == "MASUK" }
        val hasPulang = records.any { it.type == "PULANG" }

        return when {
            hasPulang -> StatusAbsen.SUDAH_PULANG
            hasMasuk -> StatusAbsen.SUDAH_MASUK
            else -> StatusAbsen.BELUM_ABSEN
        }
    }

    /**
     * Resolusi jadwal efektif: override lokal → override server → jadwal standar.
     */
    fun resolusiJadwal(
        tanggal: String,
        kelas: String,
        overridesLokal: List<OverrideJadwal>,
        overridesServer: List<OverrideJadwal>,
        jadwalStandar: JadwalStandar?
    ): JadwalEfektif? {
        // 1. Cek override lokal dulu (paling tinggi prioritas)
        val overrideLokal = overridesLokal.firstOrNull {
            it.tanggal == tanggal && (it.kelas == null || it.kelas == kelas)
        }
        if (overrideLokal != null) {
            return JadwalEfektif(
                jamMasuk = LocalTime.parse(overrideLokal.jamMasuk, fmtJam),
                jamPulang = LocalTime.parse(overrideLokal.jamPulang, fmtJam),
                sumber = SumberJadwal.OVERRIDE_LOKAL
            )
        }

        // 2. Cek override server
        val overrideServer = overridesServer.firstOrNull {
            it.tanggal == tanggal && (it.kelas == null || it.kelas == kelas)
        }
        if (overrideServer != null) {
            return JadwalEfektif(
                jamMasuk = LocalTime.parse(overrideServer.jamMasuk, fmtJam),
                jamPulang = LocalTime.parse(overrideServer.jamPulang, fmtJam),
                sumber = SumberJadwal.OVERRIDE_SERVER
            )
        }

        // 3. Jadwal standar
        if (jadwalStandar != null) {
            return JadwalEfektif(
                jamMasuk = LocalTime.parse(jadwalStandar.jamMasuk, fmtJam),
                jamPulang = LocalTime.parse(jadwalStandar.jamPulang, fmtJam),
                sumber = SumberJadwal.JADWAL_STANDAR
            )
        }

        return null
    }

    /**
     * Fungsi inti: memproses scan absensi.
     * PRD bagian 3.1–3.4 — state machine + jendela waktu.
     *
     * @param records records absensi yang sudah ada hari ini
     * @param jadwalEfektif jadwal efektif hasil resolusi
     * @param dispensasi dispensasi aktif untuk siswa ini (null jika tidak ada)
     * @param waktuSekarang waktu scan (default: sekarang)
     */
    fun prosesScan(
        records: List<RecordAbsen>,
        jadwalEfektif: JadwalEfektif?,
        dispensasi: DispensasiAktif?,
        waktuSekarang: LocalDateTime = LocalDateTime.now()
    ): DetailHasilAbsen {
        val status = tentukanStatusAbsenHarian(records)
        val jamSekarang = waktuSekarang.toLocalTime()
        val tanggal = waktuSekarang.toLocalDate().format(fmtTanggal)

        // Tidak ada jadwal → tidak bisa diproses
        if (jadwalEfektif == null) {
            return DetailHasilAbsen(
                hasil = HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK,
                catatan = "Tidak ada jadwal untuk tanggal $tanggal"
            )
        }

        val jamMasuk = jadwalEfektif.jamMasuk
        val jamPulang = jadwalEfektif.jamPulang

        return when (status) {
            StatusAbsen.BELUM_ABSEN -> prosesScanPertama(jamSekarang, jamMasuk)
            StatusAbsen.SUDAH_MASUK -> prosesScanKedua(jamSekarang, jamPulang, dispensasi)
            StatusAbsen.SUDAH_PULANG -> DetailHasilAbsen(
                hasil = HasilAbsen.DITOLAK_SUDAH_ABSEN,
                catatan = "Masuk & pulang sudah tercatat hari ini"
            )
        }
    }

    /**
     * Proses scan pertama — check-in (MASUK).
     * PRD 3.2: earliest = jamMasuk - 2 jam
     */
    private fun prosesScanPertama(
        jamSekarang: LocalTime,
        jamMasuk: LocalTime
    ): DetailHasilAbsen {
        val earliest = jamMasuk.minusHours(BATAS_AWAL_MASUK_JAM)

        // Sebelum jendela masuk
        if (jamSekarang.isBefore(earliest)) {
            return DetailHasilAbsen(
                hasil = HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK,
                catatan = "Belum waktunya absen masuk (mulai ${earliest.format(fmtJam)})"
            )
        }

        // Dalam jendela, cek keterlambatan
        val batasTepatWaktu = jamMasuk.plusMinutes(BATAS_TOLERANSI_MENIT)
        val kategoriWaktu = if (jamSekarang.isAfter(batasTepatWaktu)) {
            KategoriWaktu.TERLAMBAT
        } else {
            KategoriWaktu.NORMAL
        }

        return DetailHasilAbsen(
            hasil = HasilAbsen.BERHASIL_MASUK,
            kategoriWaktu = kategoriWaktu,
            catatan = when (kategoriWaktu) {
                KategoriWaktu.NORMAL -> "Tepat waktu · ${jamSekarang.format(fmtJam)}"
                KategoriWaktu.TERLAMBAT -> "Terlambat · masuk ${jamSekarang.format(fmtJam)}"
                else -> null
            }
        )
    }

    /**
     * Proses scan kedua — check-out (PULANG).
     * PRD 3.3: sebelum jam pulang → butuh dispensasi PULANG_CEPAT
     */
    private fun prosesScanKedua(
        jamSekarang: LocalTime,
        jamPulang: LocalTime,
        dispensasi: DispensasiAktif?
    ): DetailHasilAbsen {
        if (jamSekarang.isBefore(jamPulang)) {
            // Sebelum jam pulang — perlu dispensasi
            if (dispensasi == null) {
                return DetailHasilAbsen(
                    hasil = HasilAbsen.DITOLAK_BELUM_WAKTUNYA_PULANG,
                    catatan = "Belum waktunya absen pulang (mulai ${jamPulang.format(fmtJam)})"
                )
            }
            // Ada dispensasi
            return DetailHasilAbsen(
                hasil = HasilAbsen.BERHASIL_PULANG,
                kategoriWaktu = KategoriWaktu.DISPENSASI,
                kategoriDispensasi = dispensasi.kategori,
                catatan = "Pulang dengan izin: ${dispensasi.kategori}"
            )
        }

        // Setelah jam pulang — normal
        return DetailHasilAbsen(
            hasil = HasilAbsen.BERHASIL_PULANG,
            kategoriWaktu = KategoriWaktu.NORMAL,
            catatan = "Tepat waktu · ${jamSekarang.format(fmtJam)}"
        )
    }

    /**
     * Menentukan apakah scan harus disimpan (berdasarkan safety gate).
     * Jika ON_SITE_TESTING_SELESAI == false, scan tidak disimpan.
     */
    fun bolehSimpan(hasil: HasilAbsen, onSiteTestingSelesai: Boolean): Boolean {
        // Mode testing: kenali tapi jangan simpan
        if (!onSiteTestingSelesai) return false
        // Hanya simpan jika berhasil
        return hasil == HasilAbsen.BERHASIL_MASUK || hasil == HasilAbsen.BERHASIL_PULANG
    }
}

// --- Data classes murni (tanpa dependency Android) ---

data class RecordAbsen(
    val recordId: String,
    val siswaId: Long,
    val tanggal: String,
    val type: String, // "MASUK" atau "PULANG"
    val jamAktual: String,
    val statusKehadiranOtomatis: String,
    val catatan: String?,
    val synced: Boolean = false
)

data class JadwalStandar(
    val jamMasuk: String,
    val jamPulang: String
)

data class JadwalEfektif(
    val jamMasuk: LocalTime,
    val jamPulang: LocalTime,
    val sumber: SumberJadwal
)

enum class SumberJadwal {
    JADWAL_STANDAR,
    OVERRIDE_SERVER,
    OVERRIDE_LOKAL
}

data class OverrideJadwal(
    val id: String,
    val tanggal: String,
    val kelas: String?,
    val jamMasuk: String,
    val jamPulang: String,
    val alasan: String?,
    val terkirim: Boolean = false,
    val statusPush: String = "pending" // "pending" | "ok" | "ditolak"
)

data class DispensasiAktif(
    val siswaId: Long,
    val tanggal: String,
    val jenis: String,
    val kategori: String,
    val alasan: String?
)
