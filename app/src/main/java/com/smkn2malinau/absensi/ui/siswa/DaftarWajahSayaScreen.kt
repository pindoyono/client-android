package com.smkn2malinau.absensi.ui.siswa

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.auth.SesiPengguna
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.remote.ApiClientProvider
import com.smkn2malinau.absensi.face.MiniFasNetEngine
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.ui.CameraView
import com.smkn2malinau.absensi.ui.KioskViewModelFactory

/** Siswa mendaftarkan wajahnya SENDIRI — terkunci ke akun yang sedang login. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarWajahSayaScreen(sesi: SesiPengguna?, onTutup: () -> Unit) {
    val context = LocalContext.current
    val cm = remember { CredentialManager(context) }
    val db = remember { AbsensiDatabase.getDatabase(context, cm.getDbPassphrase()) }
    val lensaDepan = remember { cm.lensaKameraDepan() }
    val faceEngine = remember { MiniFasNetEngine(context) }
    LaunchedEffect(faceEngine) {
        runCatching {
            faceEngine.loadModels(KioskViewModelFactory.LIVENESS_MODEL, KioskViewModelFactory.EMBEDDING_MODEL)
        }
    }

    val vm: DaftarWajahSayaViewModel = viewModel(
        factory = DaftarWajahSayaViewModel.Factory(
            faceEngine, db, cm.getFaceKey(),
            siswaId = sesi?.siswaId ?: -1,
            namaSiswa = sesi?.nama ?: "",
        ) {
            val id = cm.getDeviceId(); val key = cm.getApiKey()
            if (id != null && key != null) ApiClientProvider.create(id, key, cm.getServerBaseUrl()) else null
        }
    )
    val state by vm.uiState.collectAsState()
    val latestFrame = remember { mutableStateOf<ByteArray?>(null) }

    var izinKamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val minta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { izinKamera = it }
    LaunchedEffect(Unit) { if (!izinKamera) minta.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daftar Wajah Saya", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onTutup) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${sesi?.nama ?: "-"} · ${sesi?.identitas ?: "-"}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hadap kamera lurus, pastikan wajah jelas & cukup terang, lalu tekan \"Ambil & Daftar\". " +
                    "Setelah dikirim, pendaftaran menunggu verifikasi admin sebelum bisa dipakai absen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (izinKamera) {
                        CameraView(
                            modifier = Modifier.fillMaxSize(),
                            lensDepan = lensaDepan,
                            onFrameAnalysis = { latestFrame.value = it },
                        )
                    } else {
                        Text("Izin kamera diperlukan.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.pesan?.let {
                Surface(
                    color = when {
                        state.sukses -> MaterialTheme.colorScheme.primaryContainer
                        state.pesanError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.sukses) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (state.sukses) {
                Button(onClick = onTutup, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Selesai") }
            } else {
                Button(
                    onClick = { latestFrame.value?.let(vm::daftarWajah) },
                    enabled = izinKamera && !state.sedangProses && latestFrame.value != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.sedangProses) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.sedangProses) "Memproses…" else "Ambil & Daftar")
                }
            }
        }
    }
}
