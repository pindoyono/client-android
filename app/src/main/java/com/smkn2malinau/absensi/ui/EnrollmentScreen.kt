package com.smkn2malinau.absensi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.SiswaCache
import com.smkn2malinau.absensi.face.MiniFasNetEngine
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.sync.SyncWorker

/**
 * Layar daftar wajah siswa — data siswa diambil dari cache server (`siswa_cache`),
 * operator cari nama/NIS lalu pilih, kamera untuk ambil wajah.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentScreen(
    onBack: () -> Unit,
    onSelesai: () -> Unit,
) {
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager(context) }
    val db = remember { AbsensiDatabase.getDatabase(context, credentialManager.getDbPassphrase()) }
    val lensaDepan = remember { credentialManager.lensaKameraDepan() }
    val faceEngine = remember { MiniFasNetEngine(context) }
    LaunchedEffect(faceEngine) {
        try {
            faceEngine.loadModels(
                KioskViewModelFactory.LIVENESS_MODEL,
                KioskViewModelFactory.EMBEDDING_MODEL
            )
        } catch (e: Exception) {
            // Model gagal dimuat — daftarWajah akan melaporkan error.
        }
    }
    val viewModel: EnrollmentViewModel = viewModel(
        factory = EnrollmentViewModel.Factory(faceEngine, db, credentialManager.getFaceKey())
    )
    val state by viewModel.uiState.collectAsState()
    val latestFrame = remember { mutableStateOf<ByteArray?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        // Tarik daftar siswa terbaru dari server saat layar dibuka.
        SyncWorker.enqueueSekali(context)
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daftar Wajah", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Preview kamera (ukuran tetap, tidak memenuhi layar) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (hasCameraPermission) {
                        CameraView(
                            modifier = Modifier.fillMaxSize(),
                            lensDepan = lensaDepan,
                            onFrameAnalysis = { bytes -> latestFrame.value = bytes }
                        )
                    } else {
                        Text(
                            "Izin kamera diperlukan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.siswaTerpilih == null) {
                PemilihSiswa(state, viewModel)
            } else {
                PanelDaftar(
                    state = state,
                    frameSiap = latestFrame.value != null,
                    onDaftar = { latestFrame.value?.let(viewModel::daftarWajah) },
                    onGanti = viewModel::batalPilih,
                    onSelesai = onSelesai,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PemilihSiswa(state: EnrollmentUiState, viewModel: EnrollmentViewModel) {
    OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::onQueryChange,
        label = { Text("Cari nama atau NIS") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { viewModel.onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Hapus")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    val info = when {
        state.totalSiswa == 0 -> "Belum ada data siswa. Jalankan sinkronisasi (Panel Admin) atau pastikan device online."
        state.query.isBlank() -> "${state.totalSiswa} siswa tersinkron — ketik untuk mencari."
        state.hasilCari.isEmpty() -> "Tidak ada siswa cocok dengan \"${state.query}\"."
        else -> "${state.hasilCari.size} hasil${if (state.hasilCari.size >= 40) "+ (persempit pencarian)" else ""}."
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            info,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.totalSiswa == 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = viewModel::muatSiswa) { Text("Muat ulang") }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.hasilCari, key = { it.siswa_id }) { siswa ->
            BarisSiswa(
                siswa = siswa,
                sudahEnroll = siswa.siswa_id in state.sudahEnroll,
                onClick = { viewModel.pilihSiswa(siswa) },
            )
        }
    }
}

@Composable
private fun BarisSiswa(siswa: SiswaCache, sudahEnroll: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    siswa.nama,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${siswa.nis} · ${siswa.kelas}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sudahEnroll) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Sudah punya wajah",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PanelDaftar(
    state: EnrollmentUiState,
    frameSiap: Boolean,
    onDaftar: () -> Unit,
    onGanti: () -> Unit,
    onSelesai: () -> Unit,
) {
    val siswa = state.siswaTerpilih ?: return
    val sudahEnroll = siswa.siswa_id in state.sudahEnroll

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    siswa.nama,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${siswa.nis} · ${siswa.kelas}" + if (sudahEnroll) "  ·  sudah punya wajah" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onGanti, enabled = !state.sedangProses) { Text("Ganti") }
        }
    }

    Spacer(Modifier.height(4.dp))

    Button(
        onClick = onDaftar,
        enabled = !state.sedangProses && frameSiap,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (state.sedangProses) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(if (sudahEnroll) "Daftar Ulang Wajah" else "Daftarkan Wajah", fontSize = 16.sp)
    }

    if (!frameSiap) {
        Text(
            "Menunggu kamera siap…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    state.pesan?.let { pesan ->
        Text(
            pesan,
            color = if (state.pesanError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (state.sukses) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onGanti, modifier = Modifier.weight(1f)) { Text("Siswa lain") }
            Button(onClick = onSelesai, modifier = Modifier.weight(1f)) { Text("Selesai") }
        }
    }

    Spacer(Modifier.weight(1f))
}
