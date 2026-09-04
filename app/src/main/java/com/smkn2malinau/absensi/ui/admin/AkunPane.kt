package com.smkn2malinau.absensi.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.auth.Role
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.ui.theme.AbsensiColors
import com.smkn2malinau.absensi.ui.theme.Spasi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkunPane(vm: AkunViewModel = viewModel(factory = AkunViewModel.Factory(LocalContext.current))) {
    val state by vm.uiState.collectAsState()
    var setPwUntuk by remember { mutableStateOf<String?>(null) }
    var pwBaru by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(Spasi.lg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spasi.md),
    ) {
        JudulPane("Akun & Role", "Login Panel Admin: online = Google, offline = email/NIS + password.")

        Surface(
            color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                Text("Tambah akun", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                    Role.entries.forEach { r ->
                        FilterChip(
                            selected = state.role == r,
                            onClick = { vm.onRole(r) },
                            label = { Text(r.label) },
                        )
                    }
                }
                OutlinedTextField(
                    state.identitas, vm::onIdentitas, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.role == Role.SISWA) "NIS siswa" else "Email") },
                )
                OutlinedTextField(
                    state.nama, vm::onNama, label = { Text("Nama") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state.password, vm::onPassword, label = { Text("Password (opsional — bisa di-set nanti)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = vm::tambah, modifier = Modifier.align(Alignment.End)) { Text("Simpan akun") }
            }
        }

        Text("Akun aktif (${state.akun.size})", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
        state.akun.forEach { a ->
            BarisAkun(
                a,
                onSetPassword = { setPwUntuk = a.identitas; pwBaru = "" },
                onNonaktif = { vm.nonaktifkan(a.identitas) },
            )
        }

        state.pesan?.let {
            Text(
                it,
                color = if (state.pesanError) MaterialTheme.colorScheme.error else AbsensiColors.SuksesTeks,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    setPwUntuk?.let { id ->
        AlertDialog(
            onDismissRequest = { setPwUntuk = null },
            title = { Text("Set password: $id") },
            text = {
                OutlinedTextField(
                    pwBaru, { pwBaru = it }, label = { Text("Password baru (min. 6)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setPassword(id, pwBaru); setPwUntuk = null }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { setPwUntuk = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun BarisAkun(a: AkunLokal, onSetPassword: () -> Unit, onNonaktif: () -> Unit) {
    Surface(
        color = AbsensiColors.Surface, shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spasi.md, vertical = Spasi.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(a.nama, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${a.identitas} · ${Role.dari(a.role).label}" +
                        if (a.password_hash == null) " · belum ada password" else "",
                    style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                )
            }
            TextButton(onClick = onSetPassword) { Text("Password") }
            IconButton(onClick = onNonaktif) {
                Icon(Icons.Default.Delete, "Nonaktifkan", tint = AbsensiColors.InkMuted)
            }
        }
    }
}
