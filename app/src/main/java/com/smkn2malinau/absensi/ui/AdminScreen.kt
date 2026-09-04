package com.smkn2malinau.absensi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.ui.theme.AbsensiColors
import com.smkn2malinau.absensi.ui.theme.Spasi

/**
 * Layar Admin/Setup (PRD bagian 6.5).
 *
 * Dua jalur konfigurasi:
 *  1. "Daftar dengan Google" — otomatis (setara OAuth client Windows):
 *     Google Sign-In → /auth/login/google → /device/register → simpan api key.
 *  2. Manual — admin menempel device_id + api_key dari dashboard (fallback).
 */
@Composable
fun AdminScreen(
    onSaveSuccess: () -> Unit,
    onOpenEnrollment: () -> Unit = {},
    viewModel: AdminViewModel = viewModel(factory = AdminViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(Spasi.lg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spasi.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spasi.xs)) {
                Text("Setup Device Kiosk", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Daftarkan perangkat ini ke server sebelum dipakai absensi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AbsensiColors.InkSoft
                )
            }

            OutlinedTextField(
                value = state.namaLokasi,
                onValueChange = viewModel::onNamaLokasiChange,
                label = { Text("Nama lokasi (mis. Gerbang Utama)") },
                singleLine = true,
                enabled = !state.sedangProses,
                modifier = Modifier.fillMaxWidth()
            )

            // --- Jalur 1: registrasi otomatis via Google ---
            KartuSeksi("Cara cepat") {
                Button(
                    onClick = { viewModel.daftarDenganGoogle(context, onSaveSuccess) },
                    enabled = state.googleTersedia && !state.sedangProses,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state.sedangProses) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(Spasi.sm))
                    }
                    Text("Daftar dengan Google Sekolah")
                }
                Text(
                    if (state.googleTersedia)
                        "Akun @smkn2malinau.sch.id / guru.smk.belajar.id / admin.smk.belajar.id."
                    else
                        "Google Sign-In belum dikonfigurasi (GOOGLE_WEB_CLIENT_ID). Pakai cara manual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AbsensiColors.InkMuted
                )
            }

            // --- Jalur 2: manual ---
            KartuSeksi("Manual (dari dashboard)") {
                OutlinedTextField(
                    value = state.deviceId,
                    onValueChange = viewModel::onDeviceIdChange,
                    label = { Text("Device ID") },
                    singleLine = true,
                    enabled = !state.sedangProses,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text("Device API Key") },
                    singleLine = true,
                    enabled = !state.sedangProses,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.faceKey,
                    onValueChange = viewModel::onFaceKeyChange,
                    label = { Text("Face Encryption Key (FACE_ENCRYPTION_KEY server)") },
                    singleLine = true,
                    enabled = !state.sedangProses,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = {
                        Text(
                            "Fernet key dari .env server (44 karakter). Wajib untuk mengenali wajah siswa dari server.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.emailAdmin,
                    onValueChange = viewModel::onEmailAdminChange,
                    label = { Text("Email admin lokal (opsional)") },
                    singleLine = true,
                    enabled = !state.sedangProses,
                    supportingText = {
                        Text(
                            "Isi HANYA jika Google tidak tersedia. Bikin 1 akun admin untuk login offline. " +
                                "Kalau pakai Google, role admin/guru ditentukan server otomatis.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.passwordAdmin,
                    onValueChange = viewModel::onPasswordAdminChange,
                    label = { Text("Password admin lokal") },
                    singleLine = true,
                    enabled = !state.sedangProses,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.modeTestingAktif,
                        onCheckedChange = viewModel::onModeTestingChange,
                        enabled = !state.sedangProses
                    )
                    Spacer(Modifier.width(Spasi.sm))
                    Text("Mode testing aktif — hasil tidak disimpan (PRD 10)")
                }
            }

            state.pesan?.let { pesan ->
                Surface(
                    color = if (state.pesanError) AbsensiColors.BahayaBg else AbsensiColors.SuksesBg,
                    contentColor = if (state.pesanError) AbsensiColors.BahayaTeks else AbsensiColors.SuksesTeks,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        pesan,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spasi.md)
                    )
                }
            }

            Button(
                onClick = { viewModel.simpanManual(onSaveSuccess) },
                enabled = !state.sedangProses,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Simpan konfigurasi")
            }

            KartuSeksi("Daftar wajah siswa") {
                Text(
                    "Daftarkan wajah siswa secara lokal (tanpa dashboard) untuk uji coba absensi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AbsensiColors.InkMuted
                )
                Button(
                    onClick = onOpenEnrollment,
                    enabled = !state.sedangProses,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Daftar Wajah Baru")
                }
            }
        }
    }
}

@Composable
private fun KartuSeksi(judul: String, isi: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = AbsensiColors.Surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spasi.md),
            verticalArrangement = Arrangement.spacedBy(Spasi.md)
        ) {
            Text(
                judul,
                style = MaterialTheme.typography.labelMedium,
                color = AbsensiColors.InkMuted
            )
            isi()
        }
    }
}
