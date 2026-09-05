package com.smkn2malinau.absensi.ui.siswa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.auth.SesiPengguna
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.ui.theme.AbsensiColors
import com.smkn2malinau.absensi.ui.theme.Spasi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Layar read-only untuk role siswa: riwayat absen masuk/pulang miliknya sendiri. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatSiswaScreen(sesi: SesiPengguna?, onTutup: () -> Unit) {
    val context = LocalContext.current
    val siswaId = sesi?.siswaId
    var records by remember { mutableStateOf<List<AbsensiLokal>?>(null) }
    val gantiPasswordVm: GantiPasswordViewModel =
        viewModel(factory = GantiPasswordViewModel.Factory(context))
    val gantiPasswordState by gantiPasswordVm.uiState.collectAsState()

    LaunchedEffect(siswaId) {
        records = if (siswaId == null) emptyList() else withContext(Dispatchers.IO) {
            val cm = CredentialManager(context)
            AbsensiDatabase.getDatabase(context, cm.getDbPassphrase())
                .absensiDao().recordSiswa(siswaId, 60)
        }
    }

    if (gantiPasswordState.terbuka && sesi != null) {
        DialogGantiPassword(sesi.identitas, gantiPasswordState, gantiPasswordVm)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Absensi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onTutup) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup")
                    }
                },
                actions = {
                    IconButton(onClick = gantiPasswordVm::buka) {
                        Icon(Icons.Default.Lock, contentDescription = "Ubah Password")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                sesi?.nama ?: "-",
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                records == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                siswaId == null -> Text(
                    "Akun siswa ini belum ditautkan ke data siswa. Minta admin menautkan NIS di Panel Admin → Akun.",
                    color = AbsensiColors.InkSoft,
                )
                records!!.isEmpty() -> Text("Belum ada catatan absensi.", color = AbsensiColors.InkMuted)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(records!!, key = { it.record_id }) { r -> BarisRiwayat(r) }
                }
            }
        }
    }
}

@Composable
private fun BarisRiwayat(r: AbsensiLokal) {
    Surface(
        color = AbsensiColors.Surface,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spasi.md, vertical = Spasi.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(r.tanggal, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${r.type} · ${r.jam_aktual.take(5)} · ${r.status_kehadiran_otomatis}",
                    style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                )
            }
            Text(
                if (r.synced == 1) "tersinkron" else "menunggu",
                style = MaterialTheme.typography.labelSmall,
                color = if (r.synced == 1) AbsensiColors.SuksesTeks else AbsensiColors.WarningTeks,
            )
        }
    }
}

@Composable
private fun DialogGantiPassword(
    identitas: String,
    state: GantiPasswordUiState,
    vm: GantiPasswordViewModel,
) {
    AlertDialog(
        onDismissRequest = { if (!state.sibuk) vm.tutup() },
        title = { Text("Ubah Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                OutlinedTextField(
                    state.passwordLama, vm::onPasswordLama, label = { Text("Password lama") },
                    singleLine = true, enabled = !state.sibuk,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state.passwordBaru, vm::onPasswordBaru, label = { Text("Password baru (min. 6)") },
                    singleLine = true, enabled = !state.sibuk,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state.passwordKonfirmasi, vm::onPasswordKonfirmasi, label = { Text("Ulangi password baru") },
                    singleLine = true, enabled = !state.sibuk,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.pesan?.let {
                    Text(
                        it,
                        color = if (state.pesanError) MaterialTheme.colorScheme.error else AbsensiColors.InkSoft,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.simpan(identitas) {} }, enabled = !state.sibuk) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = vm::tutup, enabled = !state.sibuk) { Text("Batal") }
        },
    )
}
