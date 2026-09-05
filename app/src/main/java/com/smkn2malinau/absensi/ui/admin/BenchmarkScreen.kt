package com.smkn2malinau.absensi.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.ui.CameraView
import com.smkn2malinau.absensi.ui.theme.AbsensiColors
import com.smkn2malinau.absensi.ui.theme.Spasi

/**
 * Uji performa nyata di device ini: latensi deteksi wajah + liveness + embedding (ONNX),
 * dan pencocokan wajah (dekripsi + jarak) — lalu ekstrapolasi perkiraan throughput kiosk.
 * Dipakai admin sebelum go-live untuk menjawab "device ini sanggup berapa siswa/menit?"
 * tanpa menebak — semua angka diukur langsung, bukan asumsi (lihat rekomendasi #4 kajian skala).
 */
@Composable
fun BenchmarkPane(vm: BenchmarkViewModel = viewModel(factory = BenchmarkViewModel.Factory(LocalContext.current))) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val lensaDepan = remember { CredentialManager(context).lensaKameraDepan() }

    var izinKamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcherIzin = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        izinKamera = it
    }
    LaunchedEffect(Unit) {
        if (!izinKamera) launcherIzin.launch(Manifest.permission.CAMERA)
    }

    Column(
        Modifier.fillMaxSize().padding(Spasi.lg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spasi.md),
    ) {
        JudulPane(
            "Benchmark Perangkat",
            "Ukur langsung kecepatan pengenalan wajah di HP ini — bukan perkiraan.",
        )

        KartuInfoPerangkat(state.infoPerangkat)
        KartuCacheWajah(state.jumlahWajahCache)

        Surface(
            color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                Text("Kamera uji", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)

                Box(
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                        .background(AbsensiColors.Bg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (izinKamera) {
                        CameraView(modifier = Modifier.fillMaxSize(), lensDepan = lensaDepan, onFrameAnalysis = vm::onFrame)
                        if (!state.berjalan && state.hasil == null) {
                            Text(
                                "Hadapkan wajah, lalu tekan Mulai",
                                color = AbsensiColors.Ink,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.background(AbsensiColors.Bg.copy(alpha = 0.6f)).padding(Spasi.sm),
                            )
                        }
                    } else {
                        Text("Izin kamera diperlukan untuk benchmark.", color = AbsensiColors.InkSoft, textAlign = TextAlign.Center)
                    }
                }

                if (state.berjalan) {
                    LinearProgressIndicator(
                        progress = { state.progress / state.target.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = AbsensiColors.Aksen, trackColor = AbsensiColors.Surface2,
                    )
                    Text(
                        "Memproses frame ${state.progress}/${state.target}…",
                        style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                    Button(onClick = vm::mulai, enabled = izinKamera && state.modelSiap && !state.berjalan) {
                        if (!state.modelSiap) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(Spasi.sm))
                            Text("Memuat model…")
                        } else {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spasi.sm))
                            Text(if (state.hasil == null) "Mulai Benchmark" else "Ulangi Benchmark")
                        }
                    }
                    if (state.berjalan) {
                        OutlinedButton(onClick = vm::batal) { Text("Batal") }
                    }
                }
            }
        }

        state.hasil?.let { KartuHasilBenchmark(it, state.jumlahWajahCache) }

        state.pesan?.let {
            Text(
                it,
                color = if (state.pesanError) MaterialTheme.colorScheme.error else AbsensiColors.InkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun KartuInfoPerangkat(info: InfoPerangkat) {
    Surface(
        color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Perangkat ini", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
            Text(info.model.ifBlank { "…" }, style = MaterialTheme.typography.bodyMedium, color = AbsensiColors.Ink)
            Text(
                "${info.android} · ${info.cpuCore} core CPU · Akselerasi: ${info.akselerasi}",
                style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
            )
        }
    }
}

@Composable
private fun KartuCacheWajah(jumlah: Int) {
    Surface(
        color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spasi.md).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Wajah di cache device ini", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
                Text(
                    "Pencocokan dibandingkan ke $jumlah wajah — makin banyak, makin lama tiap scan.",
                    style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                )
            }
            Text("$jumlah", style = MaterialTheme.typography.headlineMedium, color = AbsensiColors.Aksen)
        }
    }
}

@Composable
private fun KartuHasilBenchmark(hasil: BenchmarkHasil, jumlahCache: Int) {
    Surface(
        color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.md)) {
            Text("Hasil benchmark", style = MaterialTheme.typography.titleMedium, color = AbsensiColors.Ink)

            Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), modifier = Modifier.fillMaxWidth()) {
                Metrik("${hasil.wajahTerdeteksi}/${hasil.totalFrame}", "Wajah terdeteksi", AbsensiColors.InkSoft, Modifier.weight(1f))
                Metrik("${hasil.lolosLiveness}/${hasil.totalFrame}", "Lolos liveness", AbsensiColors.InkSoft, Modifier.weight(1f))
            }

            Text("Inference per frame (deteksi + liveness + embedding)", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), modifier = Modifier.fillMaxWidth()) {
                Metrik("${hasil.rataInferensiMs} ms", "Rata-rata", AbsensiColors.Aksen, Modifier.weight(1f))
                Metrik("${hasil.minInferensiMs}–${hasil.maxInferensiMs} ms", "Rentang", AbsensiColors.InkSoft, Modifier.weight(1f))
            }

            if (hasil.matchDinginMs != null) {
                HorizontalDivider(color = AbsensiColors.Border)
                Text(
                    "Pencocokan wajah (n = $jumlahCache siswa terenroll)",
                    style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), modifier = Modifier.fillMaxWidth()) {
                    Metrik("${hasil.matchDinginMs} ms", "Pertama (dekripsi semua)", AbsensiColors.WarningTeks, Modifier.weight(1f))
                    Metrik("${hasil.matchHangatMs} ms", "Berikutnya (cache aktif)", AbsensiColors.SuksesTeks, Modifier.weight(1f))
                }
                Text(
                    "Kiosk berjalan lama → sebagian besar scan memakai jalur \"cache aktif\" di atas.",
                    style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                )
            } else {
                Text(
                    "Belum ada embedding di cache — tambah data wajah dulu (Daftar Wajah) supaya angka pencocokan representatif.",
                    style = MaterialTheme.typography.bodySmall, color = AbsensiColors.WarningTeks,
                )
            }

            HorizontalDivider(color = AbsensiColors.Border)
            Text("Perkiraan throughput kiosk ini", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), modifier = Modifier.fillMaxWidth()) {
                Metrik("${hasil.perkiraanMsPerScan} ms", "Per scan (estimasi)", AbsensiColors.Aksen, Modifier.weight(1f))
                Metrik("${hasil.perkiraanScanPerMenit}/menit", "Siswa per menit", AbsensiColors.Aksen, Modifier.weight(1f))
            }

            val menitTeks = if (hasil.menitUntuk1000Siswa.isFinite())
                "${"%.0f".format(hasil.menitUntuk1000Siswa)} menit" else "tak terhingga"
            Text(
                "Untuk 1000 siswa lewat 1 kiosk ini: ~$menitTeks. " +
                    "Untuk jendela masuk 20 menit, disarankan " +
                    (if (hasil.kioskDisarankanUntuk20Menit > 0) "${hasil.kioskDisarankanUntuk20Menit} kiosk paralel." else "device ini tidak layak dipakai sendirian."),
                style = MaterialTheme.typography.bodyMedium, color = AbsensiColors.Ink,
            )
            Text(
                "Catatan: angka ini best-case (satu wajah terus-menerus di depan kamera, tanpa waktu jalan siswa). " +
                    "Ulangi kalau device, jumlah siswa terenroll, atau kondisi cahaya berubah signifikan.",
                style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkMuted,
            )
        }
    }
}

@Composable
private fun Metrik(nilai: String, label: String, warna: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(nilai, style = MaterialTheme.typography.titleMedium, color = warna)
        Text(label, style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkMuted)
    }
}
