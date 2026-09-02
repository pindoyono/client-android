package com.smkn2malinau.absensi.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.business.AttendanceLogic
import com.smkn2malinau.absensi.business.HasilAbsen
import com.smkn2malinau.absensi.face.FaceEngine
import com.smkn2malinau.absensi.repository.AbsensiRepository
import com.smkn2malinau.absensi.repository.SiswaCocok
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
    /** Pemuatan model ONNX — dijalankan sekali saat ViewModel dibuat. */
    private val muatModel: suspend () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        KioskUiState(onSiteTestingSelesai = onSiteTestingSelesai())
    )
    val uiState: StateFlow<KioskUiState> = _uiState.asStateFlow()

    private val sedangProses = AtomicBoolean(false)
    @Volatile private var terakhirDiprosesMs = 0L
    @Volatile private var hasilTampilSampaiMs = 0L

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
        // Status jaringan sungguhan dari NetworkMonitor (bukan konstanta ONLINE).
        viewModelScope.launch {
            onlineFlow.collect { online ->
                _uiState.update {
                    it.copy(statusJaringan = if (online) StatusJaringan.ONLINE else StatusJaringan.OFFLINE)
                }
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
    }

    fun refreshModeTesting() {
        _uiState.update { it.copy(onSiteTestingSelesai = onSiteTestingSelesai()) }
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
        val deteksi = faceEngine.prosesFrame(frame)
        if (!deteksi.wajahTerdeteksi) return

        if (!deteksi.lolosLiveness || deteksi.embedding == null) {
            tampilkan(HasilScan(StatusHasil.WAJAH_TIDAK_DIKENALI, pesan = "Wajah tidak valid"))
            return
        }

        val match: SiswaCocok = repo.cariSiswaCocok(deteksi.embedding)
        if (!match.ditemukan) {
            tampilkan(HasilScan(StatusHasil.WAJAH_TIDAK_DIKENALI))
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
                tersimpan = repo.simpanAbsensi(match.siswaId, hasil, statusOtomatis, dispensasi?.alasan)
            }
        }

        tampilkan(petakan(hasil, match, tersimpan))
    }

    private fun tampilkan(hasil: HasilScan) {
        hasilTampilSampaiMs = System.currentTimeMillis() + TAMPIL_MS
        _uiState.update { it.copy(hasilTerakhir = hasil) }
    }

    private fun petakan(hasil: HasilAbsen, match: SiswaCocok, tersimpan: Boolean): HasilScan {
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
            else -> ""
        }
        return HasilScan(
            status = status,
            nama = match.nama,
            kelas = match.kelas,
            nis = match.nis,
            pesan = pesan
        )
    }

    companion object {
        private val JAM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val THROTTLE_MS = 600L
        private const val TAMPIL_MS = 4000L
    }
}
