package com.smkn2malinau.absensi.ui.siswa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.EmbeddingCache
import com.smkn2malinau.absensi.data.remote.ApiService
import com.smkn2malinau.absensi.data.remote.EnrollWajahRequest
import com.smkn2malinau.absensi.face.CryptoEmbedding
import com.smkn2malinau.absensi.face.FaceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

/**
 * Daftar wajah MANDIRI oleh siswa yang sedang login (NIS). Terkunci ke
 * `siswaId` — siswa tidak bisa memilih orang lain. Selain embedding, kirim
 * FOTO capture ke server sebagai bukti; absensi ditolak sampai admin
 * memverifikasi lewat dashboard "Verifikasi Daftar Wajah".
 */
data class DaftarWajahSayaUi(
    val sedangProses: Boolean = false,
    val pesan: String? = null,
    val pesanError: Boolean = false,
    val sukses: Boolean = false,
)

class DaftarWajahSayaViewModel(
    private val faceEngine: FaceEngine,
    private val db: AbsensiDatabase,
    private val faceKey: String,
    private val siswaId: Int,
    private val namaSiswa: String,
    private val apiProvider: () -> ApiService?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DaftarWajahSayaUi())
    val uiState: StateFlow<DaftarWajahSayaUi> = _uiState.asStateFlow()

    fun daftarWajah(frameBytes: ByteArray) {
        if (_uiState.value.sedangProses || _uiState.value.sukses) return
        if (siswaId <= 0) { gagal("Akun siswa ini belum tertaut ke server. Hubungi admin."); return }
        _uiState.update { it.copy(sedangProses = true, pesan = "Memproses wajah…", pesanError = false) }

        viewModelScope.launch {
            try {
                val deteksi = faceEngine.prosesFrameEnroll(frameBytes)
                val embedding = deteksi.embedding
                if (embedding == null) {
                    gagal(
                        when (deteksi.alasanGagal) {
                            "wajah_tidak_terdeteksi" -> "Wajah tidak terdeteksi. Dekatkan wajah, hadap lurus, cukup terang."
                            "engine_belum_siap" -> "Model wajah belum siap. Tunggu sebentar."
                            else -> "Gagal membaca wajah (${deteksi.alasanGagal ?: "?"}). Coba lagi."
                        }
                    )
                    return@launch
                }
                if (faceKey.isBlank()) { gagal("Konfigurasi kunci wajah belum lengkap. Hubungi admin."); return@launch }

                val api = apiProvider() ?: run { gagal("Device belum terhubung ke server."); return@launch }
                val fotoB64 = withContext(Dispatchers.IO) { kompresFotoBase64(frameBytes) }

                val err = withContext(Dispatchers.IO) {
                    runCatching {
                        api.enrollWajah(
                            siswaId,
                            EnrollWajahRequest(
                                embedding = embedding.toList(),
                                modelVersion = MODEL_VERSION,
                                mandiri = true,
                                fotoJpeg = fotoB64,
                            ),
                        )
                        // Simpan juga lokal (terenkripsi) supaya kiosk bisa langsung
                        // mengenali — tapi absensi tetap ditolak sampai diverifikasi
                        // (server kirim enroll_mandiri_pending=true di sync berikutnya).
                        val enc = CryptoEmbedding.encryptEmbedding(embedding, faceKey)
                        db.siswaDao().insertEmbedding(
                            listOf(
                                EmbeddingCache(
                                    siswa_id = siswaId,
                                    embedding_encrypted = enc,
                                    model_version = MODEL_VERSION,
                                    diperbarui_pada = LocalDateTime.now().toString(),
                                )
                            )
                        )
                    }.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
                }

                if (err == null) {
                    _uiState.update {
                        it.copy(
                            sedangProses = false, sukses = true, pesanError = false,
                            pesan = "Wajah $namaSiswa terkirim. Menunggu verifikasi admin — " +
                                "setelah dikonfirmasi kamu bisa absen dengan wajah.",
                        )
                    }
                } else {
                    gagal("Gagal mengirim ke server ($err). Pastikan internet aktif, lalu coba lagi.")
                }
            } catch (e: Exception) {
                Log.e("DaftarWajahSaya", "daftarWajah gagal", e)
                gagal("Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun gagal(pesan: String) =
        _uiState.update { it.copy(sedangProses = false, pesan = pesan, pesanError = true, sukses = false) }

    /** JPEG frame → di-scale ~480px sisi terpanjang, quality 70, base64. ~20–40 KB. */
    private fun kompresFotoBase64(jpeg: ByteArray): String {
        val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: return Base64.encodeToString(jpeg, Base64.NO_WRAP)
        return try {
            val maks = 480
            val skala = maks.toFloat() / maxOf(src.width, src.height)
            val bmp = if (skala < 1f)
                Bitmap.createScaledBitmap(src, (src.width * skala).toInt(), (src.height * skala).toInt(), true)
            else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
            if (bmp !== src) bmp.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (!src.isRecycled) src.recycle()
        }
    }

    class Factory(
        private val faceEngine: FaceEngine,
        private val db: AbsensiDatabase,
        private val faceKey: String,
        private val siswaId: Int,
        private val namaSiswa: String,
        private val apiProvider: () -> ApiService?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DaftarWajahSayaViewModel(faceEngine, db, faceKey, siswaId, namaSiswa, apiProvider) as T
    }

    companion object {
        private const val MODEL_VERSION = "arcface-android"
    }
}
