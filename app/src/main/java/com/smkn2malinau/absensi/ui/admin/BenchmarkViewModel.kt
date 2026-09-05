package com.smkn2malinau.absensi.ui.admin

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.face.FaceEngine
import com.smkn2malinau.absensi.face.MiniFasNetEngine
import com.smkn2malinau.absensi.repository.AbsensiRepositoryImpl
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.ui.KioskViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/** Info statis perangkat — konteks untuk menafsirkan angka benchmark. */
data class InfoPerangkat(
    val model: String = "",
    val android: String = "",
    val cpuCore: Int = 0,
    val akselerasi: String = "…",
)

/** Ringkasan hasil satu sesi benchmark — semua angka diukur nyata di device ini, saat ini. */
data class BenchmarkHasil(
    val totalFrame: Int,
    val wajahTerdeteksi: Int,
    val lolosLiveness: Int,
    val rataInferensiMs: Long,
    val minInferensiMs: Long,
    val maxInferensiMs: Long,
    /** Pencocokan wajah PERTAMA kali (cache kosong → dekripsi semua embedding). */
    val matchDinginMs: Long?,
    /** Pencocokan wajah KEDUA dengan embedding sama (cache terisi — lihat AbsensiRepository). */
    val matchHangatMs: Long?,
    val perkiraanMsPerScan: Long,
    val perkiraanScanPerMenit: Int,
    val menitUntuk1000Siswa: Double,
    val kioskDisarankanUntuk20Menit: Int,
)

data class BenchmarkUiState(
    val infoPerangkat: InfoPerangkat = InfoPerangkat(),
    val modelSiap: Boolean = false,
    val jumlahWajahCache: Int = 0,
    val berjalan: Boolean = false,
    val progress: Int = 0,
    val target: Int = TARGET_FRAME,
    val hasil: BenchmarkHasil? = null,
    val pesan: String? = null,
    val pesanError: Boolean = false,
) {
    companion object { const val TARGET_FRAME = 15 }
}

/**
 * Benchmark nyata di device — mengukur latensi deteksi+liveness+embedding (ONNX)
 * dan pencocokan wajah (dekripsi + jarak) memakai frame kamera sungguhan, lalu
 * mengekstrapolasi perkiraan throughput/jumlah kiosk untuk skala tertentu (mis. 1000 siswa).
 * Engine & repo TERPISAH dari kiosk (instance sendiri) supaya tidak mengganggu jalur absensi.
 */
class BenchmarkViewModel(
    private val faceEngine: FaceEngine,
    private val repo: AbsensiRepositoryImpl,
    private val db: AbsensiDatabase,
    private val ambangJarak: Float,
    private val muatModel: suspend () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BenchmarkUiState())
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    private val sedangProses = AtomicBoolean(false)
    private val waktuInferensiMs = mutableListOf<Long>()
    private var jumlahTerdeteksi = 0
    private var jumlahLolosLiveness = 0
    private var embeddingTerakhir: FloatArray? = null

    init {
        _uiState.update {
            it.copy(
                infoPerangkat = InfoPerangkat(
                    model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    android = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                    cpuCore = Runtime.getRuntime().availableProcessors(),
                )
            )
        }
        viewModelScope.launch {
            runCatching { muatModel() }
                .onFailure { pesanError("Gagal memuat model: ${it.message}") }
            _uiState.update {
                it.copy(
                    modelSiap = true,
                    infoPerangkat = it.infoPerangkat.copy(akselerasi = faceEngine.statusAkselerasi()),
                )
            }
        }
        viewModelScope.launch {
            val n = runCatching { db.siswaDao().countEmbedding() }.getOrDefault(0)
            _uiState.update { it.copy(jumlahWajahCache = n) }
        }
    }

    fun mulai() {
        val s = _uiState.value
        if (s.berjalan || !s.modelSiap) return
        waktuInferensiMs.clear()
        jumlahTerdeteksi = 0
        jumlahLolosLiveness = 0
        embeddingTerakhir = null
        _uiState.update { it.copy(berjalan = true, progress = 0, hasil = null, pesan = null) }
    }

    fun batal() {
        _uiState.update { it.copy(berjalan = false) }
    }

    /** Dipanggil CameraView untuk tiap frame — hanya diproses selama benchmark berjalan. */
    fun onFrame(frame: ByteArray) {
        if (!_uiState.value.berjalan) return
        if (!sedangProses.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val mulai = System.nanoTime()
                val hasil = faceEngine.prosesFrame(frame)
                val ms = (System.nanoTime() - mulai) / 1_000_000

                if (!_uiState.value.berjalan) return@launch // dibatalkan selagi frame diproses
                waktuInferensiMs.add(ms)
                if (hasil.wajahTerdeteksi) jumlahTerdeteksi++
                if (hasil.lolosLiveness) jumlahLolosLiveness++
                hasil.embedding?.let { embeddingTerakhir = it }

                val progress = waktuInferensiMs.size
                _uiState.update { it.copy(progress = progress) }
                if (progress >= _uiState.value.target) selesai()
            } finally {
                sedangProses.set(false)
            }
        }
    }

    private suspend fun selesai() {
        _uiState.update { it.copy(berjalan = false) }
        if (waktuInferensiMs.isEmpty()) {
            pesanError("Tidak ada frame terproses — coba lagi.")
            return
        }

        val rata = waktuInferensiMs.average().toLong()
        val min = waktuInferensiMs.min()
        val max = waktuInferensiMs.max()

        var matchDingin: Long? = null
        var matchHangat: Long? = null
        embeddingTerakhir?.let { emb ->
            val t1 = System.nanoTime()
            repo.cariSiswaCocok(emb, ambangJarak)
            matchDingin = (System.nanoTime() - t1) / 1_000_000

            val t2 = System.nanoTime()
            repo.cariSiswaCocok(emb, ambangJarak)
            matchHangat = (System.nanoTime() - t2) / 1_000_000
        }

        // Perkiraan waktu per scan pada kondisi steady-state kiosk (matching sudah ter-cache).
        val msPerScan = rata + (matchHangat ?: matchDingin ?: 0L)
        val scanPerMenit = if (msPerScan <= 0) 0 else (60_000L / msPerScan).toInt()
        val menitUntuk1000 = if (scanPerMenit <= 0) Double.POSITIVE_INFINITY else 1000.0 / scanPerMenit
        val kioskUntuk20Menit = when {
            scanPerMenit <= 0 -> -1
            else -> ceil(menitUntuk1000 / 20.0).toInt().coerceAtLeast(1)
        }

        _uiState.update {
            it.copy(
                hasil = BenchmarkHasil(
                    totalFrame = waktuInferensiMs.size,
                    wajahTerdeteksi = jumlahTerdeteksi,
                    lolosLiveness = jumlahLolosLiveness,
                    rataInferensiMs = rata,
                    minInferensiMs = min,
                    maxInferensiMs = max,
                    matchDinginMs = matchDingin,
                    matchHangatMs = matchHangat,
                    perkiraanMsPerScan = msPerScan,
                    perkiraanScanPerMenit = scanPerMenit,
                    menitUntuk1000Siswa = menitUntuk1000,
                    kioskDisarankanUntuk20Menit = kioskUntuk20Menit,
                )
            )
        }
    }

    fun bersihkanPesan() = _uiState.update { it.copy(pesan = null) }
    private fun pesanError(m: String) = _uiState.update { it.copy(pesan = m, pesanError = true) }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cm = CredentialManager(appContext)
            val db = AbsensiDatabase.getDatabase(appContext, cm.getDbPassphrase())
            val faceEngine = MiniFasNetEngine(appContext)
            return BenchmarkViewModel(
                faceEngine = faceEngine,
                repo = AbsensiRepositoryImpl(db, cm.getDeviceId() ?: "unknown-device", cm.getFaceKey()),
                db = db,
                ambangJarak = cm.getAmbangJarak(),
                muatModel = {
                    faceEngine.loadModels(KioskViewModelFactory.LIVENESS_MODEL, KioskViewModelFactory.EMBEDDING_MODEL)
                },
            ) as T
        }
    }
}
