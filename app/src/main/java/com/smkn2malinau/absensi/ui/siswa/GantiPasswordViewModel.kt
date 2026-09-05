package com.smkn2malinau.absensi.ui.siswa

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.auth.AuthRepository
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.remote.ApiClientProvider
import com.smkn2malinau.absensi.security.CredentialManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GantiPasswordUiState(
    val terbuka: Boolean = false,
    val passwordLama: String = "",
    val passwordBaru: String = "",
    val passwordKonfirmasi: String = "",
    val sibuk: Boolean = false,
    val pesan: String? = null,
    val pesanError: Boolean = false,
)

/** Ganti password sendiri untuk akun siswa yang sedang login — dipakai [RiwayatSiswaScreen]. */
class GantiPasswordViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GantiPasswordUiState())
    val uiState: StateFlow<GantiPasswordUiState> = _uiState.asStateFlow()

    fun buka() = _uiState.update { GantiPasswordUiState(terbuka = true) }
    fun tutup() = _uiState.update { GantiPasswordUiState(terbuka = false) }

    fun onPasswordLama(v: String) = _uiState.update { it.copy(passwordLama = v, pesan = null) }
    fun onPasswordBaru(v: String) = _uiState.update { it.copy(passwordBaru = v, pesan = null) }
    fun onPasswordKonfirmasi(v: String) = _uiState.update { it.copy(passwordKonfirmasi = v, pesan = null) }

    fun simpan(identitas: String, onSukses: () -> Unit) {
        val s = _uiState.value
        if (s.sibuk) return
        if (s.passwordBaru != s.passwordKonfirmasi) {
            _uiState.update { it.copy(pesan = "Konfirmasi password baru tidak cocok.", pesanError = true) }
            return
        }
        _uiState.update { it.copy(sibuk = true, pesan = null) }
        viewModelScope.launch {
            val err = auth.ubahPasswordSendiri(identitas, s.passwordLama, s.passwordBaru)
            if (err != null) {
                _uiState.update { it.copy(sibuk = false, pesan = err, pesanError = true) }
            } else {
                _uiState.update { GantiPasswordUiState(terbuka = false) }
                onSukses()
            }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cm = CredentialManager(appContext)
            val db = AbsensiDatabase.getDatabase(appContext, cm.getDbPassphrase())
            val api = ApiClientProvider.createForRegistration(cm.getServerBaseUrl())
            return GantiPasswordViewModel(AuthRepository(db.akunDao(), cm, api)) as T
        }
    }
}
