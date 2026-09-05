package com.smkn2malinau.absensi.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.auth.AuthRepository
import com.smkn2malinau.absensi.auth.HasilLogin
import com.smkn2malinau.absensi.auth.Role
import com.smkn2malinau.absensi.auth.SesiPengguna
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.remote.ApiClientProvider
import com.smkn2malinau.absensi.device.GoogleIdTokenProvider
import com.smkn2malinau.absensi.security.CredentialManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val belumAdaAkun: Boolean = false,        // belum ada akun apa pun → wajib login Google (online) dulu
    val googleTersedia: Boolean = false,
    val identitas: String = "",
    val password: String = "",
    val butuhBuatPassword: Boolean = false,   // akun ada tapi belum punya password offline
    val sibuk: Boolean = false,
    val pesan: String? = null,
    val pesanError: Boolean = false,
)

class LoginViewModel(
    private val auth: AuthRepository,
    private val googleProvider: GoogleIdTokenProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(googleTersedia = googleProvider.terkonfigurasi))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(belumAdaAkun = !auth.adaAkun()) }
        }
    }

    fun onIdentitas(v: String) = _uiState.update { it.copy(identitas = v, pesan = null) }
    fun onPassword(v: String) = _uiState.update { it.copy(password = v, pesan = null) }

    fun loginGoogle(activityContext: Context, onSukses: (SesiPengguna) -> Unit) {
        if (_uiState.value.sibuk) return
        _uiState.update { it.copy(sibuk = true, pesan = "Membuka Google Sign-In…", pesanError = false) }
        viewModelScope.launch {
            when (val t = googleProvider.ambilIdToken(activityContext)) {
                is GoogleIdTokenProvider.Hasil.Token -> tangani(auth.loginGoogle(t.idToken), onSukses)
                is GoogleIdTokenProvider.Hasil.Dibatalkan -> gagal("Login Google dibatalkan.")
                is GoogleIdTokenProvider.Hasil.TidakAdaAkun -> gagal("Tidak ada akun Google di perangkat ini.")
                is GoogleIdTokenProvider.Hasil.Gagal -> gagal("Google Sign-In gagal: ${t.pesan}")
            }
        }
    }

    fun loginPassword(onSukses: (SesiPengguna) -> Unit) {
        val s = _uiState.value
        if (s.sibuk) return
        if (s.identitas.isBlank() || s.password.isBlank()) return gagal("Isi email/NIS dan password.")
        _uiState.update { it.copy(sibuk = true, pesan = null) }
        viewModelScope.launch {
            when (val h = auth.loginPassword(s.identitas, s.password)) {
                is HasilLogin.ButuhPassword ->
                    _uiState.update {
                        it.copy(
                            sibuk = false, butuhBuatPassword = true,
                            pesan = "Akun ${h.nama} (${h.role.label}) belum punya password offline. Buat sekarang.",
                            pesanError = false,
                        )
                    }
                else -> tangani(h, onSukses)
            }
        }
    }

    fun buatPassword(onSukses: (SesiPengguna) -> Unit) {
        val s = _uiState.value
        if (s.sibuk) return
        _uiState.update { it.copy(sibuk = true, pesan = null) }
        viewModelScope.launch {
            tangani(auth.buatPasswordLaluLogin(s.identitas, s.password), onSukses)
        }
    }

    private fun tangani(hasil: HasilLogin, onSukses: (SesiPengguna) -> Unit) {
        when (hasil) {
            is HasilLogin.Sukses -> {
                _uiState.update { it.copy(sibuk = false, pesan = null) }
                onSukses(hasil.sesi)
            }
            is HasilLogin.Gagal -> gagal(hasil.pesan)
            is HasilLogin.ButuhPassword ->
                _uiState.update { it.copy(sibuk = false, butuhBuatPassword = true, pesan = "Buat password offline.", pesanError = false) }
        }
    }

    private fun gagal(pesan: String) = _uiState.update { it.copy(sibuk = false, pesan = pesan, pesanError = true) }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        private val activityContext = context
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cm = CredentialManager(appContext)
            val db = AbsensiDatabase.getDatabase(appContext, cm.getDbPassphrase())
            val api = ApiClientProvider.createForRegistration(cm.getServerBaseUrl())
            return LoginViewModel(
                auth = AuthRepository(
                    db.akunDao(), cm, api,
                    resolveSiswaId = { nis -> db.siswaDao().getSemuaSiswa().firstOrNull { it.nis == nis }?.siswa_id },
                ),
                googleProvider = GoogleIdTokenProvider(activityContext),
            ) as T
        }
    }
}
