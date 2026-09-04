package com.smkn2malinau.absensi.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.EmbeddingCache
import com.smkn2malinau.absensi.data.local.entity.SiswaCache
import com.smkn2malinau.absensi.face.CryptoEmbedding
import com.smkn2malinau.absensi.face.FaceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Enrollment wajah siswa di kiosk (mode testing/on-site, PRD bagian 10).
 *
 * Data siswa DIAMBIL DARI SERVER — daftar `siswa_cache` yang sudah ditarik
 * `SyncWorker` (`GET /embeddings/sync`). Operator tinggal cari nama/NIS lalu
 * pilih; embedding disimpan terhadap `siswa_id` server yang asli, jadi saat
 * sync berikutnya versi server (bila ada) menimpa bersih via PK.
 *
 * Alur: cari & pilih siswa → ambil frame kamera → liveness → ArcFace embedding
 * → enkripsi AES → simpan ke `embedding_cache`.
 */
data class EnrollmentUiState(
    val query: String = "",
    val hasilCari: List<SiswaCache> = emptyList(),
    val totalSiswa: Int = 0,
    val siswaTerpilih: SiswaCache? = null,
    /** siswa_id yang sudah punya embedding di cache (indikator "sudah didaftar"). */
    val sudahEnroll: Set<Int> = emptySet(),
    val sedangProses: Boolean = false,
    val pesan: String? = null,
    val pesanError: Boolean = false,
    val sukses: Boolean = false,
)

class EnrollmentViewModel(
    private val faceEngine: FaceEngine,
    private val db: AbsensiDatabase,
    /** Fernet key embedding — HARUS sama dengan server (`FACE_ENCRYPTION_KEY`). */
    private val faceKey: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    /** Cache seluruh siswa (dari server), sudah terurut nama. Difilter di memori. */
    private var semuaSiswa: List<SiswaCache> = emptyList()

    init {
        muatSiswa()
    }

    fun muatSiswa() {
        viewModelScope.launch {
            val siswa = withContext(Dispatchers.IO) { db.siswaDao().getSemuaSiswa() }
            val enrolled = withContext(Dispatchers.IO) {
                db.siswaDao().getSemuaEmbedding().map { it.siswa_id }.toSet()
            }
            semuaSiswa = siswa.sortedBy { it.nama.lowercase() }
            _uiState.update {
                it.copy(
                    totalSiswa = siswa.size,
                    sudahEnroll = enrolled,
                    hasilCari = filter(it.query),
                )
            }
        }
    }

    fun onQueryChange(q: String) =
        _uiState.update { it.copy(query = q, hasilCari = filter(q), sukses = false, pesan = null) }

    fun pilihSiswa(s: SiswaCache) =
        _uiState.update { it.copy(siswaTerpilih = s, pesan = null, pesanError = false, sukses = false) }

    fun batalPilih() =
        _uiState.update { it.copy(siswaTerpilih = null, pesan = null, sukses = false) }

    private fun filter(q: String): List<SiswaCache> {
        val t = q.trim()
        val basis = if (t.isEmpty()) semuaSiswa
        else semuaSiswa.filter { it.nama.contains(t, ignoreCase = true) || it.nis.contains(t, ignoreCase = true) }
        return basis.take(MAKS_HASIL)
    }

    /** Ambil wajah dari frame kamera saat ini, proses liveness+embedding, simpan. */
    fun daftarWajah(frameBytes: ByteArray) {
        val s = _uiState.value
        if (s.sedangProses) return
        val siswa = s.siswaTerpilih ?: run {
            _uiState.update { it.copy(pesan = "Pilih siswa dari daftar dulu.", pesanError = true) }
            return
        }
        _uiState.update {
            it.copy(sedangProses = true, pesan = "Memproses wajah…", pesanError = false, sukses = false)
        }

        viewModelScope.launch {
            try {
                // Enrollment tidak mengecek liveness (diawasi admin) — setara skip_liveness Windows.
                val deteksi = faceEngine.prosesFrameEnroll(frameBytes)
                when {
                    deteksi.embedding != null -> simpan(siswa, deteksi.embedding)
                    deteksi.alasanGagal == "wajah_tidak_terdeteksi" ->
                        gagal("Wajah tidak terdeteksi. Dekatkan wajah, hadap kamera lurus, pastikan cukup terang.")
                    deteksi.alasanGagal == "engine_belum_siap" ->
                        gagal("Model wajah belum siap. Tunggu sebentar lalu coba lagi.")
                    else ->
                        gagal("Gagal mengekstrak data wajah (${deteksi.alasanGagal ?: "tidak diketahui"}). Coba lagi.")
                }
            } catch (e: Exception) {
                Log.e("EnrollmentViewModel", "daftarWajah gagal", e)
                gagal("Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun simpan(siswa: SiswaCache, embedding: FloatArray) {
        if (faceKey.isBlank()) {
            gagal("FACE_ENCRYPTION_KEY belum di-set. Isi di Setup Device (harus sama dengan server).")
            return
        }
        try {
            withContext(Dispatchers.IO) {
                val encrypted = CryptoEmbedding.encryptEmbedding(embedding, faceKey)
                db.siswaDao().insertEmbedding(
                    listOf(
                        EmbeddingCache(
                            siswa_id = siswa.siswa_id,
                            embedding_encrypted = encrypted,
                            model_version = "arcface-local",
                            diperbarui_pada = LocalDateTime.now().toString(),
                        )
                    )
                )
            }
        } catch (e: CryptoEmbedding.KunciWajahSalah) {
            gagal("Key wajah tidak valid: ${e.message}")
            return
        }
        _uiState.update {
            it.copy(
                sedangProses = false,
                sukses = true,
                pesanError = false,
                pesan = "Wajah ${siswa.nama} (${siswa.nis}) terdaftar. Siap absen.",
                sudahEnroll = it.sudahEnroll + siswa.siswa_id,
            )
        }
    }

    private fun gagal(pesan: String) = _uiState.update {
        it.copy(sedangProses = false, pesan = pesan, pesanError = true, sukses = false)
    }

    class Factory(
        private val faceEngine: FaceEngine,
        private val db: AbsensiDatabase,
        private val faceKey: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EnrollmentViewModel::class.java))
            return EnrollmentViewModel(faceEngine, db, faceKey) as T
        }
    }

    companion object {
        private const val MAKS_HASIL = 40

        /**
         * ID untuk enroll LOKAL — selalu negatif, terpisah dari `siswa_id` server (positif).
         * Dipertahankan untuk kompatibilitas / jalur enroll manual bila kelak dibutuhkan.
         */
        fun idEnrollLokal(nis: String): Int {
            val h = nis.trim().hashCode()
            val positif = if (h == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(h)
            return -(positif % 1_900_000_000 + 1)
        }
    }
}
