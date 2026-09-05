package com.smkn2malinau.absensi.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.business.AttendanceLogic
import com.smkn2malinau.absensi.business.HasilAbsen
import com.smkn2malinau.absensi.face.FaceEngine
import com.smkn2malinau.absensi.face.LivenessEvaluator
import com.smkn2malinau.absensi.data.local.dao.RiwayatAbsenRow
import com.smkn2malinau.absensi.repository.AbsensiRepository
import com.smkn2malinau.absensi.repository.RingkasanKiosk
import com.smkn2malinau.absensi.repository.SiswaCocok
import com.smkn2malinau.absensi.sync.SyncWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lapisan "lem" yang menyambungkan CameraView → FaceEngine → matching →
 * AttendanceLogic.hitungHasil() → simpan DB → KioskUiState (PRD bagian 4 & 5).
 */
class KioskViewModel(
    private val faceEngine: FaceEngine,
    private val attendanceLogic: AttendanceLogic,
    private val repo: AbsensiRepository,
    private val onlineFlow: Flow<Boolean> = flowOf(true),
    /** PRD bagian 10 — bila false: wajah tetap dikenali tapi TIDAK disimpan. */
    private val onSiteTestingSelesai: () -> Boolean,
    private val jamProvider: () -> LocalTime = { LocalTime.now() },
    private val tanggalProvider: () -> LocalDate = { LocalDate.now() },
    /** Ambang *distance* face-matching — bisa dikalibrasi runtime dari Panel Admin. */
    private val ambangJarak: Float = LivenessEvaluator.AMBANG_JARAK_DEFAULT,
    /** Picu satu siklus sync (mis. `SyncWorker.enqueueSekali`) — dipanggil tiap absen tersimpan + berkala. */
    private val picuSinkron: () -> Unit = {},
    /** Pemuatan model ONNX — dijalankan sekali saat ViewModel dibuat. */
    private val muatModel: suspend () -> Unit = {},
    /** Geofencing (opt-in per device) — hasil cek terakhir dari SyncService, lihat CredentialManager. */
    private val lokasiValidProvider: () -> Boolean = { true },
    private val lokasiAlasanProvider: () -> String? = { null },
    private val lokasiJarakProvider: () -> Double? = { null },
    private val lokasiDikonfigurasiProvider: () -> Boolean = { false },
    /** true kalau cek geofencing terakhir mendeteksi lokasi mock (fake GPS) —
     *  distempel ke record absensi supaya server bisa menandainya. */
    private val lokasiMockProvider: () -> Boolean = { false },
    /** Sync manual dari tombol header kiosk (paksa=true, beda dari picuSinkron yang di-debounce). */
    private val paksaSinkron: () -> Unit = {},
    /** Observasi WorkInfo WORK_SEKALI supaya tombol sync manual punya umpan balik nyata (spinner). */
    private val workManager: WorkManager? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        KioskUiState(
            onSiteTestingSelesai = onSiteTestingSelesai(),
            lokasiValid = lokasiValidProvider(),
            lokasiAlasan = lokasiAlasanProvider(),
            lokasiJarakMeter = lokasiJarakProvider(),
            lokasiDikonfigurasi = lokasiDikonfigurasiProvider(),
        )
    )
    val uiState: StateFlow<KioskUiState> = _uiState.asStateFlow()

    private val sedangProses = AtomicBoolean(false)
    @Volatile private var terakhirDiprosesMs = 0L
    @Volatile private var hasilTampilSampaiMs = 0L

    // Dua sinyal penyusun pil status kiri-atas (jaringan + hasil siklus sync terakhir).
    @Volatile private var jaringanOnline = true
    @Volatile private var sinkronTerakhirSukses = false
    @Volatile private var picuSinkronTerakhirMs = 0L

    init {
        viewModelScope.launch {
            try {
                muatModel()
            } catch (e: Exception) {
                Log.e("KioskViewModel", "Gagal memuat model ONNX", e)
            }
        }
        // Jam sungguhan — di-update tiap detik (PRD bagian 4.4).
        viewModelScope.launch {
            while (isActive) {
                _uiState.update { it.copy(jamSekarang = jamProvider().format(JAM_FMT)) }
                delay(1000)
            }
        }
        // Status jaringan sungguhan dari NetworkMonitor — digabung dgn hasil siklus sync.
        viewModelScope.launch {
            onlineFlow.collect { online ->
                jaringanOnline = online
                _uiState.update { it.copy(statusJaringan = hitungStatusJaringan()) }
            }
        }
        // Reset kartu hasil setelah beberapa detik supaya kiosk siap scan berikutnya.
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val batas = hasilTampilSampaiMs
                if (batas != 0L && System.currentTimeMillis() > batas) {
                    hasilTampilSampaiMs = 0L
                    _uiState.update { it.copy(hasilTerakhir = null) }
                }
            }
        }
        // Status bar sinkronisasi (jam masuk/pulang, badge kesegaran, ringkasan sync).
        viewModelScope.launch {
            while (isActive) {
                muatRingkasanDanLokasi()
                delay(RINGKASAN_REFRESH_MS)
            }
        }
        // Tombol sync manual di header kiosk — pantau WorkInfo WORK_SEKALI supaya
        // tombol benar-benar terlihat bekerja (spinner selagi jalan, ringkasan
        // langsung dimuat ulang begitu selesai) alih-alih diam tanpa umpan balik.
        workManager?.let { wm ->
            viewModelScope.launch {
                wm.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_SEKALI).collect { infos ->
                    val sebelumnya = _uiState.value.sedangSync
                    val berjalan = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                    _uiState.update { it.copy(sedangSync = berjalan) }
                    // Baru saja selesai (transisi jalan -> tidak jalan) -> muat ulang segera,
                    // jangan tunggu tick RINGKASAN_REFRESH_MS berikutnya (bisa sampai 15 detik).
                    if (sebelumnya && !berjalan) muatRingkasanDanLokasi()
                }
            }
        }
        // Daftar 5 absensi terakhir (nama + masuk/pulang + keterangan dispensasi) — kartu riwayat kiosk.
        muatRiwayatAbsen()
        // Picu sync berkala selama kiosk aktif — WorkManager periodik minimal 15 mnt,
        // jadi ini yang bikin absen naik ke server dlm ~1-2 menit (mirip loop 45 dtk Windows).
        viewModelScope.launch {
            while (isActive) {
                delay(SINKRON_BERKALA_MS)
                picuSinkronDebounce()
            }
        }
    }

    /** Panggil `picuSinkron` paling sering tiap PICU_SINKRON_MIN_MS (hindari thrash saat absen beruntun). */
    private fun picuSinkronDebounce() {
        val now = System.currentTimeMillis()
        if (now - picuSinkronTerakhirMs < PICU_SINKRON_MIN_MS) return
        picuSinkronTerakhirMs = now
        runCatching { picuSinkron() }
    }

    private fun KioskUiState.terapkanRingkasan(r: RingkasanKiosk): KioskUiState {
        sinkronTerakhirSukses = r.sinkronTerakhirSukses
        return copy(
            statusJaringan = hitungStatusJaringan(),
            ringkasanSync = RingkasanSyncUi(
                waktuTeks = r.syncTerakhir?.format(SYNC_FMT) ?: "belum pernah",
                antreKirim = r.antreKirim,
                jumlahWajah = r.jumlahWajah,
                jumlahJadwal = r.jumlahJadwal,
            ),
            jadwalMasuk = r.jadwalHariIni?.jamMasuk?.format(JAM_FMT),
            jadwalPulang = r.jadwalHariIni?.jamPulang?.format(JAM_FMT),
            jadwalOverride = r.jadwalOverride,
            kesegaran = when {
                !r.kesegaran.diketahui -> KesegaranUi.TIDAK_DIKETAHUI
                r.kesegaran.segar -> KesegaranUi.SEGAR
                else -> KesegaranUi.BASI
            },
            dataBasi = r.kesegaran.dataBasi,
        )
    }

    /**
     * Pil status: OFFLINE bila tak ada jaringan; ONLINE hanya bila siklus sync
     * terakhir sukses; selain itu SINKRON_TERTUNDA (jaringan ada tapi sync
     * gagal / belum jalan) — jadi "tersinkron" benar-benar berarti tersinkron.
     */
    private fun hitungStatusJaringan(): StatusJaringan = when {
        !jaringanOnline -> StatusJaringan.OFFLINE
        sinkronTerakhirSukses -> StatusJaringan.ONLINE
        else -> StatusJaringan.SINKRON_TERTUNDA
    }

    fun refreshModeTesting() {
        _uiState.update { it.copy(onSiteTestingSelesai = onSiteTestingSelesai()) }
    }

    /** Tombol sync manual di header kiosk — hasilnya lihat observer WorkInfo di init. */
    fun syncSekarang() = paksaSinkron()

    private suspend fun muatRingkasanDanLokasi() {
        runCatching { repo.ringkasanKiosk(tanggalProvider().toString()) }
            .onFailure { Log.w("KioskViewModel", "Gagal memuat ringkasan kiosk", it) }
            .getOrNull()
            ?.let { r -> _uiState.update { it.terapkanRingkasan(r) } }
        // Geofencing — baca status cek terakhir (ditulis SyncService secara berkala).
        _uiState.update {
            it.copy(
                lokasiValid = lokasiValidProvider(),
                lokasiAlasan = lokasiAlasanProvider(),
                lokasiJarakMeter = lokasiJarakProvider(),
                lokasiDikonfigurasi = lokasiDikonfigurasiProvider(),
            )
        }
    }

    /** Dipanggil oleh CameraView untuk tiap frame hasil ImageAnalysis. */
    fun onFrameCaptured(frame: ByteArray) {
        val now = System.currentTimeMillis()
        if (now - terakhirDiprosesMs < THROTTLE_MS) return
        if (!sedangProses.compareAndSet(false, true)) return
        terakhirDiprosesMs = now
        viewModelScope.launch {
            try {
                prosesFrame(frame)
            } catch (e: Exception) {
                Log.e("KioskViewModel", "Gagal memproses frame", e)
            } finally {
                sedangProses.set(false)
            }
        }
    }

    /** Diekspos untuk test E2E — jalur yang sama tanpa throttle/guard. */
    suspend fun prosesFrame(frame: ByteArray) {
        // Geofencing — kiosk di luar lokasi yang diizinkan tidak memproses wajah sama
        // sekali (bukan cuma UI disembunyikan; ini mencegah absensi tersimpan sungguhan).
        if (!lokasiValidProvider()) return

        val deteksi = faceEngine.prosesFrame(frame)
        if (!deteksi.wajahTerdeteksi) return

        if (!deteksi.lolosLiveness || deteksi.embedding == null) {
            tampilkan(HasilScan(StatusHasil.WAJAH_TIDAK_DIKENALI, pesan = "Wajah tidak valid"))
            return
        }

        val match: SiswaCocok = repo.cariSiswaCocok(deteksi.embedding, ambangJarak)
        if (!match.ditemukan) {
            val diagnostik = when {
                match.jumlahDibandingkan == 0 ->
                    "Tidak ada data wajah terbaca — cek FACE_ENCRYPTION_KEY & sinkronisasi"
                match.jarak == Float.MAX_VALUE -> ""
                else -> "Terdekat ${"%.2f".format(match.jarak)} dari ambang ${"%.2f".format(ambangJarak)} · ${match.jumlahDibandingkan} wajah dibanding"
            }
            tampilkan(HasilScan(StatusHasil.WAJAH_TIDAK_DIKENALI, diagnostik = diagnostik))
            return
        }

        val tanggal = tanggalProvider().toString()
        val jadwal = repo.jadwalEfektif(match.kelas, tanggal)
        if (jadwal == null) {
            tampilkan(
                HasilScan(
                    StatusHasil.DITOLAK_BELUM_WAKTUNYA,
                    nama = match.nama, kelas = match.kelas, nis = match.nis,
                    pesan = "Jadwal belum tersedia"
                )
            )
            return
        }

        val status = repo.statusHariIni(match.siswaId, tanggal)
        val dispensasi = repo.dispensasiAktif(match.siswaId, tanggal)

        val hasil = attendanceLogic.hitungHasil(
            jamProvider(), jadwal, status,
            adaDispensasiPulangCepat = dispensasi != null
        )

        var tersimpan = false
        if (hasil.berhasil()) {
            val statusOtomatis = when (hasil) {
                HasilAbsen.BERHASIL_PULANG_CEPAT ->
                    dispensasi?.kategori?.takeIf { it.isNotBlank() } ?: "IZIN"
                HasilAbsen.BERHASIL_MASUK_TERLAMBAT -> "TERLAMBAT"
                else -> "NORMAL"
            }
            // GERBANG UJI LAPANGAN (PRD bagian 10): mode testing => JANGAN simpan ke DB.
            if (onSiteTestingSelesai()) {
                tersimpan = repo.simpanAbsensi(match.siswaId, hasil, statusOtomatis, dispensasi?.alasan, lokasiMockProvider())
                if (tersimpan) {
                    picuSinkronDebounce() // kirim ke server secepatnya
                    muatRiwayatAbsen()
                }
            }
        }

        tampilkan(petakan(hasil, match, tersimpan, jadwal))
    }

    private fun muatRiwayatAbsen() {
        viewModelScope.launch {
            runCatching { repo.riwayatAbsenTerbaru(5) }
                .onFailure { Log.w("KioskViewModel", "Gagal memuat riwayat absen", it) }
                .getOrNull()
                ?.map(::formatRiwayat)
                ?.let { baris -> _uiState.update { it.copy(riwayatAbsen = baris) } }
        }
    }

    /** "Nama · Masuk/Pulang" + keterangan dispensasi (mis. sakit/izin) bila bukan hadir normal. */
    private fun formatRiwayat(row: RiwayatAbsenRow): String {
        val jenis = if (row.type == "PULANG") "Pulang" else "Masuk"
        val status = row.status_kehadiran_otomatis.trim()
        val keterangan = when {
            status.isBlank() || status.equals("NORMAL", ignoreCase = true) -> null
            row.catatan.isNotBlank() -> "$status (${row.catatan})"
            else -> status
        }
        return listOfNotNull(row.nama, jenis, keterangan).joinToString(" · ")
    }

    private fun tampilkan(hasil: HasilScan) {
        hasilTampilSampaiMs = System.currentTimeMillis() + TAMPIL_MS
        _uiState.update { it.copy(hasilTerakhir = hasil) }
    }

    private fun petakan(
        hasil: HasilAbsen,
        match: SiswaCocok,
        tersimpan: Boolean,
        jadwal: AttendanceLogic.JadwalEfektif,
    ): HasilScan {
        val modeTesting = !onSiteTestingSelesai()
        val status = when (hasil) {
            HasilAbsen.BERHASIL_MASUK_NORMAL, HasilAbsen.BERHASIL_PULANG_NORMAL -> StatusHasil.BERHASIL_TEPAT_WAKTU
            HasilAbsen.BERHASIL_MASUK_TERLAMBAT -> StatusHasil.BERHASIL_TERLAMBAT
            HasilAbsen.BERHASIL_PULANG_CEPAT -> StatusHasil.BERHASIL_PULANG_DISPENSASI
            HasilAbsen.DITOLAK_SUDAH_ABSEN_LENGKAP -> StatusHasil.DITOLAK_SUDAH_ABSEN
            HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK, HasilAbsen.DITOLAK_BELUM_WAKTUNYA_PULANG -> StatusHasil.DITOLAK_BELUM_WAKTUNYA
            else -> StatusHasil.WAJAH_TIDAK_DIKENALI
        }
        val pesan = when {
            hasil.berhasil() && modeTesting -> "Dikenali (mode testing — tidak disimpan)"
            hasil.berhasil() && tersimpan -> "Absen tersimpan"
            hasil.berhasil() && !tersimpan -> "Sudah absen hari ini"
            hasil == HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK -> "Belum waktunya absen masuk"
            hasil == HasilAbsen.DITOLAK_BELUM_WAKTUNYA_PULANG -> "Belum waktunya absen pulang"
            hasil == HasilAbsen.DITOLAK_SUDAH_ABSEN_LENGKAP -> "Sudah absen masuk & pulang hari ini"
            else -> ""
        }
        val diagnostik = when (hasil) {
            HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK -> "Jam masuk ${jadwal.jamMasuk.format(JAM_FMT)}"
            HasilAbsen.DITOLAK_BELUM_WAKTUNYA_PULANG -> "Jam pulang ${jadwal.jamPulang.format(JAM_FMT)}"
            else -> ""
        }
        return HasilScan(
            status = status,
            nama = match.nama,
            kelas = match.kelas,
            nis = match.nis,
            pesan = pesan,
            diagnostik = diagnostik,
        )
    }

    companion object {
        private val JAM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val SYNC_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        private const val THROTTLE_MS = 600L
        private const val TAMPIL_MS = 4000L
        private const val RINGKASAN_REFRESH_MS = 15_000L
        private const val SINKRON_BERKALA_MS = 90_000L
        private const val PICU_SINKRON_MIN_MS = 10_000L
    }
}
