package com.smkn2malinau.absensi.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.data.local.entity.JadwalCache
import com.smkn2malinau.absensi.data.local.entity.JadwalOverrideLokal
import com.smkn2malinau.absensi.repository.AdminRepository
import com.smkn2malinau.absensi.repository.AdminRepositoryImpl
import com.smkn2malinau.absensi.repository.SiswaLokalRow
import com.smkn2malinau.absensi.repository.StatistikSync
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminPanelUiState(
    val memuat: Boolean = true,
    val stat: StatistikSync? = null,
    val recordTerbaru: List<AbsensiLokal> = emptyList(),
    val jadwalStandar: List<JadwalCache> = emptyList(),
    val overrideLokal: List<JadwalOverrideLokal> = emptyList(),
    val siswa: List<SiswaLokalRow> = emptyList(),
    val sedangSync: Boolean = false,
    val pesan: String? = null,
    val pesanError: Boolean = false,
    val serverUrl: String = "",
    val serverUrlDefault: String = "",
    val lensaDepan: Boolean = true,
    val faceKey: String = "",
    val ambangJarak: Float = 0.3542f,
)

class AdminPanelViewModel(
    private val repo: AdminRepository,
    private val credentialManager: CredentialManager,
    private val workManager: WorkManager,
    private val serverUrlDefault: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdminPanelUiState(
            serverUrl = credentialManager.getServerBaseUrl() ?: "",
            serverUrlDefault = serverUrlDefault,
            lensaDepan = credentialManager.lensaKameraDepan(),
            faceKey = credentialManager.getFaceKey(),
            ambangJarak = credentialManager.getAmbangJarak(),
        )
    )
    val uiState: StateFlow<AdminPanelUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_SEKALI).collect { infos ->
                val berjalan = infos.any { it.state == WorkInfo.State.RUNNING }
                // ENQUEUED + runAttemptCount 0 = benar-benar menunggu jaringan.
                // ENQUEUED + runAttemptCount > 0 = retry setelah siklus gagal (bukan soal jaringan).
                val menungguJaringan = infos.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0 }
                val retrySetelahGagal = infos.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }
                val sukses = infos.any { it.state == WorkInfo.State.SUCCEEDED }
                val gagal = infos.any { it.state == WorkInfo.State.FAILED }
                _uiState.update {
                    it.copy(
                        sedangSync = berjalan,
                        pesan = when {
                            berjalan -> "Sinkronisasi berjalan…"
                            menungguJaringan -> "Menunggu jaringan — sync akan jalan saat online."
                            retrySetelahGagal -> "Siklus sync gagal — dicoba lagi otomatis. Cek pesan error di bawah."
                            sukses -> "Sinkronisasi selesai."
                            gagal -> "Sinkronisasi gagal — akan dicoba lagi otomatis."
                            else -> it.pesan
                        },
                        pesanError = gagal || retrySetelahGagal,
                    )
                }
                if (!berjalan && (sukses || gagal || retrySetelahGagal)) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(memuat = true) }
            val stat = runCatching { repo.statistikSync() }.getOrNull()
            val record = runCatching { repo.recordSyncTerbaru(20) }.getOrDefault(emptyList())
            val jadwal = runCatching { repo.jadwalStandar() }.getOrDefault(emptyList())
            val override = runCatching { repo.overrideLokalSemua() }.getOrDefault(emptyList())
            val siswa = runCatching { repo.daftarSiswaLokal() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    memuat = false,
                    stat = stat,
                    recordTerbaru = record,
                    jadwalStandar = jadwal,
                    overrideLokal = override,
                    siswa = siswa,
                )
            }
        }
    }

    fun syncSekarang(context: Context) {
        _uiState.update { it.copy(pesan = "Sinkronisasi diminta…", pesanError = false) }
        SyncWorker.enqueueSekali(context, paksa = true)
    }

    fun tambahOverrideLokal(
        tanggal: String, kelas: String, jamMasuk: String, jamPulang: String, alasan: String
    ) {
        if (!validTanggal(tanggal)) return pesanError("Tanggal harus format YYYY-MM-DD.")
        if (!validJam(jamMasuk) || !validJam(jamPulang)) return pesanError("Jam harus format HH:MM (mis. 07:00).")
        viewModelScope.launch {
            runCatching {
                repo.simpanOverrideLokal(tanggal, kelas.ifBlank { null }, jamMasuk, jamPulang, alasan.ifBlank { null })
            }.onSuccess {
                pesanSukses("Override lokal disimpan. Berlaku langsung untuk absensi offline.")
                refresh()
            }.onFailure { pesanError("Gagal simpan: ${it.message}") }
        }
    }

    fun hapusOverrideLokal(id: String) {
        viewModelScope.launch {
            runCatching { repo.hapusOverrideLokal(id) }
                .onSuccess { pesanSukses("Override lokal dihapus."); refresh() }
                .onFailure { pesanError("Gagal hapus: ${it.message}") }
        }
    }

    fun resetPushDitolak() {
        viewModelScope.launch {
            val n = runCatching { repo.resetOverrideDitolak() }.getOrDefault(0)
            if (n == 0) pesanError("Tidak ada override yang ditolak server.")
            else { pesanSukses("$n override direset, akan di-push ulang saat sync."); refresh() }
        }
    }

    fun simpanServerUrl(url: String) {
        val bersih = url.trim()
        if (bersih.isNotEmpty() && !bersih.startsWith("http")) return pesanError("URL harus diawali http:// atau https://")
        if (bersih.isEmpty()) credentialManager.saveServerBaseUrl("") else credentialManager.saveServerBaseUrl(bersih)
        _uiState.update { it.copy(serverUrl = credentialManager.getServerBaseUrl() ?: "") }
        pesanSukses("URL server disimpan. Berlaku untuk sync berikutnya.")
    }

    fun setLensaDepan(depan: Boolean) {
        credentialManager.saveLensaKamera(depan)
        _uiState.update { it.copy(lensaDepan = depan) }
    }

    fun simpanAmbangJarak(nilai: Float) {
        credentialManager.saveAmbangJarak(nilai)
        _uiState.update { it.copy(ambangJarak = credentialManager.getAmbangJarak()) }
        pesanSukses("Ambang match disimpan (${"%.2f".format(nilai)}). Berlaku untuk scan berikutnya.")
    }

    fun simpanFaceKey(key: String) {
        val bersih = key.trim()
        if (bersih.isNotEmpty() && !com.smkn2malinau.absensi.validation.Validation.isValidFernetKey(bersih)) {
            return pesanError("Face key harus Fernet key (44 karakter base64url dari .env server).")
        }
        credentialManager.saveFaceKey(bersih)
        _uiState.update { it.copy(faceKey = credentialManager.getFaceKey()) }
        pesanSukses("Face key disimpan (${bersih.length} karakter). Jalankan 'Tes Face Key' untuk verifikasi.")
    }

    /** Uji key aktif terhadap embedding cache — jawaban pasti "key benar atau tidak". */
    fun tesFaceKey() {
        viewModelScope.launch {
            val key = credentialManager.getFaceKey()
            if (key.isBlank()) {
                pesanError("Face key kosong. Isi di atas lalu Simpan, atau set FACE_ENCRYPTION_KEY di local.properties.")
                return@launch
            }
            val (total, ok) = runCatching { repo.tesDekripsiEmbedding(key) }.getOrDefault(0 to 0)
            when {
                total == 0 -> pesanError("Belum ada embedding di cache. Jalankan sinkronisasi dulu.")
                ok == total -> pesanSukses("✓ Face key COCOK — $ok/$total embedding server berhasil didekripsi (${key.length} char).")
                ok == 0 -> pesanError("✗ Face key SALAH — 0/$total embedding bisa didekripsi. Samakan PERSIS dengan FACE_ENCRYPTION_KEY di .env server.")
                else -> pesanError("Sebagian ($ok/$total) — kemungkinan key baru diganti server / data lama korup.")
            }
        }
    }

    fun hapusKredensial(onSelesai: () -> Unit) {
        credentialManager.clearCredentials()
        onSelesai()
    }

    fun bersihkanPesan() = _uiState.update { it.copy(pesan = null) }

    private fun pesanSukses(m: String) = _uiState.update { it.copy(pesan = m, pesanError = false) }
    private fun pesanError(m: String) = _uiState.update { it.copy(pesan = m, pesanError = true) }

    private fun validJam(s: String): Boolean {
        val m = Regex("""(\d{1,2}):(\d{2})""").matchEntire(s.trim()) ?: return false
        val (h, mnt) = m.destructured
        return h.toInt() in 0..23 && mnt.toInt() in 0..59
    }

    private fun validTanggal(s: String) = Regex("""\d{4}-\d{2}-\d{2}""").matches(s.trim())

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cm = CredentialManager(appContext)
            val db = AbsensiDatabase.getDatabase(appContext, cm.getDbPassphrase())
            return AdminPanelViewModel(
                repo = AdminRepositoryImpl(db),
                credentialManager = cm,
                workManager = WorkManager.getInstance(appContext),
                serverUrlDefault = com.smkn2malinau.absensi.data.remote.ApiClientProvider.BASE_URL,
            ) as T
        }
    }
}
