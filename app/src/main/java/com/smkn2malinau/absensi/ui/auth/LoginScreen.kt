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
            modifier = Modifier.widthIn(max = 400.dp).padding(Spasi.lg),
        ) {
            Column(
                Modifier.padding(Spasi.xl),
                verticalArrangement = Arrangement.spacedBy(Spasi.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Lock, null, tint = AbsensiColors.InkSoft, modifier = Modifier.size(36.dp))
                Text(
                    if (state.butuhBuatPassword) "Buat Password Offline" else "Login Panel Admin",
                    style = MaterialTheme.typography.titleLarge,
                )

                if (state.belumAdaAkun && !state.butuhBuatPassword) {
                    Text(
                        "Belum ada akun di device ini. Role admin/guru ditentukan server — " +
                            "login Google (online) dulu minimal sekali.",
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

@Composable
private fun ColumnScope.LoginForm(
    state: LoginUiState,
    vm: LoginViewModel,
    context: android.content.Context,
    onSukses: (SesiPengguna) -> Unit,
) {
    if (state.googleTersedia) {
        Button(
            onClick = { vm.loginGoogle(context, onSukses) },
            enabled = !state.sibuk,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Login dengan Google (online)") }
        Text(
            "Butuh internet. Role ditentukan server. Untuk offline pakai email + password di bawah.",
            style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkMuted,
        )
        HorizontalDivider(Modifier.padding(vertical = Spasi.xs), color = AbsensiColors.Border)
    }

    KolomIdentitasPassword(state, vm)
    Button(
        onClick = { vm.loginPassword(onSukses) },
        enabled = !state.sibuk,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Login offline (email/NIS + password)") }
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
    Button(
        onClick = { vm.buatPassword(onSukses) },
        enabled = !state.sibuk,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Simpan & masuk") }
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
