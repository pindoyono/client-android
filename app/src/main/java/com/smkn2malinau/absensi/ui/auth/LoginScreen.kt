package com.smkn2malinau.absensi.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.auth.SesiPengguna
import com.smkn2malinau.absensi.ui.theme.AbsensiColors
import com.smkn2malinau.absensi.ui.theme.Spasi

/**
 * Gerbang Panel Admin — auth berbasis role.
 * Online: Google Sign-In (server tentukan role). Offline: email/NIS + password (akun lokal).
 * Akun pertama (belum ada akun) → form buat admin.
 */
@Composable
fun LoginScreen(
    onLolos: (SesiPengguna) -> Unit,
    onBatal: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory(LocalContext.current)),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    Box(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = AbsensiColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
            modifier = Modifier.widthIn(max = 380.dp).padding(Spasi.lg),
        ) {
            Column(
                Modifier.padding(Spasi.lg),
                verticalArrangement = Arrangement.spacedBy(Spasi.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Lock, null, tint = AbsensiColors.InkSoft, modifier = Modifier.size(28.dp))
                Text(
                    if (state.butuhBuatPassword) "Buat Password Offline" else "Login Panel Admin",
                    style = MaterialTheme.typography.titleLarge,
                )

                if (state.belumAdaAkun && !state.butuhBuatPassword) {
                    Text(
                        "Belum ada akun di perangkat ini. Login Google sekali dulu untuk menetapkan role.",
                        style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkMuted,
                    )
                }

                if (state.butuhBuatPassword) {
                    BuatPasswordForm(state, viewModel) { onLolos(it) }
                } else {
                    LoginForm(state, viewModel, context) { onLolos(it) }
                }

                state.pesan?.let {
                    Text(
                        it,
                        color = if (state.pesanError) MaterialTheme.colorScheme.error else AbsensiColors.InkSoft,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                TextButton(onClick = onBatal, enabled = !state.sibuk) { Text("Batal") }
            }
        }
    }
}

/** Tombol aksi utama — tinggi & lebar seragam, label selalu 1 baris. */
@Composable
private fun TombolUtama(teks: String, aktif: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = aktif,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text(teks, maxLines = 1) }
}

/** Pemisah "atau" tipis antara jalur online dan offline. */
@Composable
private fun PemisahAtau() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spasi.xs)) {
        HorizontalDivider(Modifier.weight(1f), color = AbsensiColors.Border)
        Text(
            "atau",
            style = MaterialTheme.typography.labelSmall,
            color = AbsensiColors.InkMuted,
            modifier = Modifier.padding(horizontal = Spasi.sm),
        )
        HorizontalDivider(Modifier.weight(1f), color = AbsensiColors.Border)
    }
}

@Composable
private fun ColumnScope.LoginForm(
    state: LoginUiState,
    vm: LoginViewModel,
    context: android.content.Context,
    onSukses: (SesiPengguna) -> Unit,
) {
    if (state.googleTersedia) {
        TombolUtama("Login dengan Google", !state.sibuk) { vm.loginGoogle(context, onSukses) }
        Text(
            "Perlu internet · role ditentukan server",
            style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkMuted,
        )
        PemisahAtau()
    }

    KolomIdentitasPassword(state, vm)
    TombolUtama("Login offline", !state.sibuk) { vm.loginPassword(onSukses) }
}

@Composable
private fun ColumnScope.BuatPasswordForm(state: LoginUiState, vm: LoginViewModel, onSukses: (SesiPengguna) -> Unit) {
    OutlinedTextField(
        state.identitas, {}, label = { Text("Akun") },
        singleLine = true, enabled = false, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state.password, vm::onPassword, label = { Text("Password baru (min. 6)") },
        singleLine = true, enabled = !state.sibuk,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    TombolUtama("Simpan & masuk", !state.sibuk) { vm.buatPassword(onSukses) }
}

@Composable
private fun ColumnScope.KolomIdentitasPassword(
    state: LoginUiState,
    vm: LoginViewModel,
    labelIdentitas: String = "Email / NIS",
) {
    OutlinedTextField(
        state.identitas, vm::onIdentitas, label = { Text(labelIdentitas) },
        singleLine = true, enabled = !state.sibuk, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state.password, vm::onPassword, label = { Text("Password") },
        singleLine = true, enabled = !state.sibuk,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
