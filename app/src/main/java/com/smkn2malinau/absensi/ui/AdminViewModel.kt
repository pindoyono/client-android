package com.smkn2malinau.absensi.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.data.remote.ApiClientProvider
import com.smkn2malinau.absensi.device.DeviceRegistrar
import com.smkn2malinau.absensi.device.GoogleIdTokenProvider
import com.smkn2malinau.absensi.device.HasilRegistrasi
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.validation.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val deviceId: String = "",
    val apiKey: String = "",
    val faceKey: String = "",
    /** Fallback: akun admin lokal saat Google tidak tersedia (opsional). */
    val emailAdmin: String = "",
    val passwordAdmin: String = "",
    val namaLokasi: String = "",
    val modeTestingAktif: Boolean = true,
    val sedangProses: Boolean = false,
    val googleTersedia: Boolean = false,
    val pesan: String? = null,
    val pesanError: Boolean = false,
    /** 409 — device sudah terdaftar, admin harus menulis ulang api key. */
    val butuhApiKeyManual: Boolean = false,
)

/**
 * Orkestrasi layar Admin: registrasi Google (setara OAuth Windows) + simpan manual.
 */
class AdminViewModel(
    private val credentialManager: CredentialManager,
    private val googleProvider: GoogleIdTokenProvider,
    private val registrar: DeviceRegistrar,
    private val auth: com.smkn2malinau.absensi.auth.AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdminUiState(
            deviceId = credentialManager.getDeviceId() ?: "",
            apiKey = credentialManager.getApiKey() ?: "",
            faceKey = credentialManager.getFaceKey(),
            namaLokasi = credentialManager.getNamaLokasi(),
            modeTestingAktif = !credentialManager.isOnSiteTestingSelesai(),
            googleTersedia = googleProvider.terkonfigurasi,
        )
    )
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun onDeviceIdChange(v: String) = _uiState.update { it.copy(deviceId = v) }
    fun onApiKeyChange(v: String) = _uiState.update { it.copy(apiKey = v) }
    fun onFaceKeyChange(v: String) = _uiState.update { it.copy(faceKey = v) }
    fun onEmailAdminChange(v: String) = _uiState.update { it.copy(emailAdmin = v) }
    fun onPasswordAdminChange(v: String) = _uiState.update { it.copy(passwordAdmin = v) }
    fun onNamaLokasiChange(v: String) = _uiState.update { it.copy(namaLokasi = v) }
    fun onModeTestingChange(v: Boolean) = _uiState.update { it.copy(modeTestingAktif = v) }

    /** Registrasi otomatis: Google Sign-In → /auth/login/google → /device/register. */
    fun daftarDenganGoogle(activityContext: Context, onSelesai: () -> Unit) {
        val s = _uiState.value
        if (s.sedangProses) return
        _uiState.update { it.copy(sedangProses = true, pesan = "Membuka Google Sign-In…", pesanError = false) }

        viewModelScope.launch {
            when (val tok = googleProvider.ambilIdToken(activityContext)) {
                is GoogleIdTokenProvider.Hasil.Dibatalkan ->
                    selesaiError("Login Google dibatalkan.")
                is GoogleIdTokenProvider.Hasil.TidakAdaAkun ->
                    selesaiError("Tidak ada akun Google di perangkat ini. Tambahkan akun sekolah dulu.")
                is GoogleIdTokenProvider.Hasil.Gagal ->
                    selesaiError("Google Sign-In gagal: ${tok.pesan}")
                is GoogleIdTokenProvider.Hasil.Token -> {
                    _uiState.update { it.copy(pesan = "Mendaftarkan device ke server…") }
                    val deviceIdSeed = credentialManager.getOrCreateDeviceIdSeed()
                    val namaLokasi = _uiState.value.namaLokasi.trim()
                    val hasil = registrar.registrasi(tok.idToken, deviceIdSeed, namaLokasi)
                    seedAkunDariGoogle(tok.idToken, hasil)
                    prosesHasil(hasil, onSelesai)
                }
            }
        }
    }

    /** Seed akun_lokal dari akun Google yang mendaftarkan device — role dari server. */
    private suspend fun seedAkunDariGoogle(idToken: String, hasil: HasilRegistrasi) {
        val email = com.smkn2malinau.absensi.device.GoogleIdToken.email(idToken)?.lowercase() ?: return
        val (nama, role) = when (hasil) {
            is HasilRegistrasi.Sukses -> hasil.nama to hasil.role
            is HasilRegistrasi.SudahTerdaftar -> hasil.nama to hasil.role
            else -> return
        }
        auth.seedDariServer(
            email,
            nama ?: email,
            com.smkn2malinau.absensi.auth.Role.dari(role),
        )
    }

    private fun prosesHasil(hasil: HasilRegistrasi, onSelesai: () -> Unit) {
        when (hasil) {
            is HasilRegistrasi.Sukses -> {
                credentialManager.saveDeviceId(hasil.deviceId)
                credentialManager.saveApiKey(hasil.apiKey)
                credentialManager.saveAdminInfo(hasil.nama, hasil.role)
                // Server kirim Fernet key di response register (PRD R-P1-1) → auto-isi.
                hasil.faceKey?.let { credentialManager.saveFaceKey(it) }
                _uiState.value.namaLokasi.trim().takeIf { it.isNotEmpty() }
                    ?.let { credentialManager.saveNamaLokasi(it) }
                _uiState.update {
                    it.copy(
                        sedangProses = false,
                        deviceId = hasil.deviceId,
                        apiKey = hasil.apiKey,
                        faceKey = credentialManager.getFaceKey(),
                        pesan = "Device terdaftar sebagai '${hasil.nama ?: hasil.deviceId}'" +
                            (if (hasil.faceKey != null) " · face key otomatis tersimpan" else ""),
                        pesanError = false,
                    )
                }
                onSelesai()
            }
            is HasilRegistrasi.SudahTerdaftar -> _uiState.update {
                it.copy(
                    sedangProses = false,
                    deviceId = hasil.deviceId.ifBlank { it.deviceId },
                    butuhApiKeyManual = true,
                    pesan = "Device sudah terdaftar di server. Masukkan API key device dari dashboard, lalu Simpan.",
                    pesanError = true,
                )
            }
            is HasilRegistrasi.DomainTidakDiizinkan -> selesaiError(
                "Akun ${hasil.email.ifBlank { "ini" }} bukan dari domain sekolah yang diizinkan " +
                    "(${DeviceRegistrar.DOMAIN_DIIZINKAN.joinToString()})."
            )
            is HasilRegistrasi.Gagal -> selesaiError(hasil.pesan)
        }
    }

    /** Simpan manual (fallback / setelah 409). */
    fun simpanManual(onSelesai: () -> Unit) {
        val s = _uiState.value
        val deviceId = s.deviceId.trim()
        val apiKey = s.apiKey.trim()
        if (!Validation.isValidDeviceId(deviceId)) {
            selesaiError("Device ID tidak valid (4–64 karakter: huruf, angka, _ atau -).")
            return
        }
        if (!Validation.isValidApiKey(apiKey)) {
            selesaiError("API Key tidak valid (16–128 karakter: huruf, angka, _ atau -).")
            return
        }
        val faceKey = s.faceKey.trim()
        if (faceKey.isNotEmpty() && !Validation.isValidFernetKey(faceKey)) {
            selesaiError("Face Encryption Key harus Fernet key (44 karakter base64url, dari .env server).")
            return
        }
        val emailAdmin = s.emailAdmin.trim().lowercase()
        val pwAdmin = s.passwordAdmin
        if (emailAdmin.isNotEmpty()) {
            if (!emailAdmin.contains('@')) return selesaiError("Email admin tidak valid.")
            if (pwAdmin.length < 6) return selesaiError("Password admin minimal 6 karakter.")
        }
        credentialManager.saveDeviceId(deviceId)
        credentialManager.saveApiKey(apiKey)
        credentialManager.saveFaceKey(faceKey)
        credentialManager.setOnSiteTestingSelesai(!s.modeTestingAktif)
        s.namaLokasi.trim().takeIf { it.isNotEmpty() }?.let { credentialManager.saveNamaLokasi(it) }
        viewModelScope.launch {
            if (emailAdmin.isNotEmpty()) {
                auth.seedDariServer(emailAdmin, emailAdmin, com.smkn2malinau.absensi.auth.Role.ADMIN, pwAdmin)
            }
            _uiState.update { it.copy(pesan = "Konfigurasi tersimpan.", pesanError = false, butuhApiKeyManual = false) }
            onSelesai()
        }
    }

    private fun selesaiError(msg: String) = _uiState.update {
        it.copy(sedangProses = false, pesan = msg, pesanError = true)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        private val activityContext = context
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cm = CredentialManager(appContext)
            val api = ApiClientProvider.createForRegistration(cm.getServerBaseUrl())
            val db = com.smkn2malinau.absensi.data.local.AbsensiDatabase.getDatabase(appContext, cm.getDbPassphrase())
            return AdminViewModel(
                credentialManager = cm,
                googleProvider = GoogleIdTokenProvider(activityContext),
                registrar = DeviceRegistrar(api),
                auth = com.smkn2malinau.absensi.auth.AuthRepository(db.akunDao(), cm, api),
            ) as T
        }
    }
}
