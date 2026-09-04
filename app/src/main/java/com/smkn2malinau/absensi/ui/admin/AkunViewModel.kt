package com.smkn2malinau.absensi.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smkn2malinau.absensi.auth.AuthRepository
import com.smkn2malinau.absensi.auth.Role
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.data.remote.ApiClientProvider
import com.smkn2malinau.absensi.security.CredentialManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AkunUiState(
    val akun: List<AkunLokal> = emptyList(),
    val identitas: String = "",
    val nama: String = "",
    val role: Role = Role.GURU_PIKET,
    val password: String = "",
    val pesan: String? = null,
    val pesanError: Boolean = false,
)

class AkunViewModel(
    private val auth: AuthRepository,
    private val db: AbsensiDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AkunUiState())
    val uiState: StateFlow<AkunUiState> = _uiState.asStateFlow()

    init { muat() }

    fun muat() {
        viewModelScope.launch { _uiState.update { it.copy(akun = auth.daftarAkun()) } }
    }

    fun onIdentitas(v: String) = _uiState.update { it.copy(identitas = v, pesan = null) }
    fun onNama(v: String) = _uiState.update { it.copy(nama = v, pesan = null) }
    fun onRole(v: Role) = _uiState.update { it.copy(role = v, pesan = null) }
    fun onPassword(v: String) = _uiState.update { it.copy(password = v, pesan = null) }

    fun tambah() {
        val s = _uiState.value
        viewModelScope.launch {
            val siswaId = if (s.role == Role.SISWA) {
                db.siswaDao().getSemuaSiswa().firstOrNull { it.nis == s.identitas.trim() }?.siswa_id
            } else null
            if (s.role == Role.SISWA && siswaId == null) {
                pesanError("NIS '${s.identitas}' tidak ada di data siswa. Sinkronkan dulu.")
                return@launch
            }
            val err = auth.tambahAkun(
                s.identitas, s.nama, s.role,
                s.password.ifBlank { null }, siswaId,
            )
            if (err != null) pesanError(err)
            else {
                pesanSukses("Akun '${s.identitas}' disimpan.")
                _uiState.update { it.copy(identitas = "", nama = "", password = "") }
                muat()
            }
        }
    }

    fun setPassword(identitas: String, password: String) {
        viewModelScope.launch {
            val err = auth.setPassword(identitas, password)
            if (err != null) pesanError(err) else { pesanSukses("Password '$identitas' diperbarui."); muat() }
        }
    }

    fun nonaktifkan(identitas: String) {
        viewModelScope.launch {
            val err = auth.nonaktifkanAkun(identitas)
            if (err != null) pesanError(err) else { pesanSukses("Akun '$identitas' dinonaktifkan."); muat() }
        }
    }

    fun bersihkanPesan() = _uiState.update { it.copy(pesan = null) }
    private fun pesanSukses(m: String) = _uiState.update { it.copy(pesan = m, pesanError = false) }
    private fun pesanError(m: String) = _uiState.update { it.copy(pesan = m, pesanError = true) }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cm = CredentialManager(appContext)
            val db = AbsensiDatabase.getDatabase(appContext, cm.getDbPassphrase())
            val api = ApiClientProvider.createForRegistration(cm.getServerBaseUrl())
            return AkunViewModel(AuthRepository(db.akunDao(), cm, api), db) as T
        }
    }
}
