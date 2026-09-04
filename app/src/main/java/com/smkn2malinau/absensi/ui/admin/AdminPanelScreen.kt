package com.smkn2malinau.absensi.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.smkn2malinau.absensi.auth.Fitur
import com.smkn2malinau.absensi.auth.Role
import com.smkn2malinau.absensi.auth.SesiPengguna
import com.smkn2malinau.absensi.auth.boleh
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.data.local.entity.JadwalOverrideLokal
import com.smkn2malinau.absensi.repository.SiswaLokalRow
import com.smkn2malinau.absensi.repository.StatistikSync
import com.smkn2malinau.absensi.ui.AdminScreen
import com.smkn2malinau.absensi.ui.EnrollmentScreen
import com.smkn2malinau.absensi.ui.theme.AbsensiColors
import com.smkn2malinau.absensi.ui.theme.Spasi
import java.time.LocalDate

private enum class Seksi(val label: String, val ikon: ImageVector, val fitur: Fitur) {
    SINKRONISASI("Sinkronisasi", Icons.Default.Refresh, Fitur.SINKRONISASI),
    JADWAL("Jadwal", Icons.Default.DateRange, Fitur.JADWAL),
    DATA_SISWA("Data Siswa", Icons.Default.Person, Fitur.DATA_SISWA),
    ENROLLMENT("Daftar Wajah", Icons.Default.Face, Fitur.DAFTAR_WAJAH),
    AKUN("Akun", Icons.Default.Lock, Fitur.KELOLA_AKUN),
    PERANGKAT("Perangkat", Icons.Default.Settings, Fitur.PENGATURAN),
    PENGATURAN("Pengaturan", Icons.Default.Build, Fitur.PENGATURAN),
    WEB("Dashboard Web", Icons.Default.Info, Fitur.PANEL_ADMIN),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    sesi: SesiPengguna?,
    onTutup: () -> Unit,
    onKredensialDihapus: () -> Unit,
    viewModel: AdminPanelViewModel = viewModel(factory = AdminPanelViewModel.Factory(LocalContext.current)),
) {
    if (sesi == null || !sesi.boleh(Fitur.PANEL_ADMIN)) {
        LaunchedEffect(Unit) { onTutup() }
        return
    }
    val role = sesi.role
    val seksiBoleh = remember(role) { Seksi.entries.filter { role.boleh(it.fitur) } }

    val state by viewModel.uiState.collectAsState()
    var seksi by remember { mutableStateOf(seksiBoleh.first()) }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Enrollment punya layar penuh sendiri (kamera) — tampilkan tanpa scaffold panel.
    if (seksi == Seksi.ENROLLMENT) {
        EnrollmentScreen(
            onBack = { seksi = Seksi.DATA_SISWA },
            onSelesai = { seksi = Seksi.DATA_SISWA; viewModel.refresh() },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = AbsensiColors.Surface) {
                Column(Modifier.padding(Spasi.lg)) {
                    Text("Panel Admin", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${sesi.nama} · ${role.label}",
                        style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                    )
                }
                seksiBoleh.forEach { s ->
                    NavigationDrawerItem(
                        icon = { Icon(s.ikon, null) },
                        label = { Text(s.label) },
                        selected = seksi == s,
                        onClick = {
                            seksi = s; viewModel.bersihkanPesan()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = Spasi.sm)
                    )
                }
                HorizontalDivider(color = AbsensiColors.Border, modifier = Modifier.padding(vertical = Spasi.sm))
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                    label = { Text("Logout & tutup") },
                    selected = false,
                    onClick = onTutup,
                    modifier = Modifier.padding(horizontal = Spasi.sm)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = AbsensiColors.Bg,
            topBar = {
                TopAppBar(
                    title = { Text(seksi.label) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onTutup) {
                            Icon(Icons.Default.Close, "Tutup panel")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AbsensiColors.Surface,
                        titleContentColor = AbsensiColors.Ink,
                        navigationIconContentColor = AbsensiColors.InkSoft,
                        actionIconContentColor = AbsensiColors.InkSoft,
                    )
                )
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (seksi) {
                    Seksi.SINKRONISASI -> SinkronisasiPane(state, { viewModel.syncSekarang(context) }, viewModel::refresh)
                    Seksi.JADWAL -> JadwalPane(state, viewModel::tambahOverrideLokal, viewModel::hapusOverrideLokal, viewModel::resetPushDitolak)
                    Seksi.DATA_SISWA -> DataSiswaPane(state, viewModel::tarikSiswaDariServer, viewModel::refresh)
                    Seksi.AKUN -> AkunPane()
                    Seksi.ENROLLMENT -> Unit // ditangani di atas (layar penuh)
                    Seksi.PERANGKAT -> AdminScreen(onSaveSuccess = { viewModel.refresh() }, onOpenEnrollment = { seksi = Seksi.ENROLLMENT })
                    Seksi.PENGATURAN -> PengaturanPane(
                        state, viewModel::simpanServerUrl, viewModel::setLensaDepan,
                        viewModel::simpanFaceKey, viewModel::tesFaceKey, viewModel::simpanAmbangJarak,
                    ) {
                        viewModel.hapusKredensial(onKredensialDihapus)
                    }
                    Seksi.WEB -> WebLinksPane(state.serverUrl.ifBlank { state.serverUrlDefault })
                }

                state.pesan?.let { p ->
                    PesanBar(p, state.pesanError, viewModel::bersihkanPesan, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sinkronisasi
// ---------------------------------------------------------------------------

@Composable
private fun SinkronisasiPane(
    state: AdminPanelUiState,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
) {
    val stat = state.stat
    Column(
        Modifier.fillMaxSize().padding(Spasi.lg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spasi.md)
    ) {
        JudulPane("Sinkronisasi Server", "Hijau = tersinkron, kuning = menunggu, merah = gagal (retry otomatis).")

        Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), modifier = Modifier.fillMaxWidth()) {
            KartuStat("${stat?.totalAbsensi ?: 0}", "Total absensi", AbsensiColors.InkSoft, Modifier.weight(1f))
            KartuStat("${stat?.tersinkron ?: 0}", "Tersinkron", AbsensiColors.SuksesTeks, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), modifier = Modifier.fillMaxWidth()) {
            KartuStat("${stat?.menunggu ?: 0}", "Menunggu", AbsensiColors.WarningTeks, Modifier.weight(1f))
            KartuStat("${stat?.gagal ?: 0}", "Gagal / retry", if ((stat?.gagal ?: 0) > 0) AbsensiColors.BahayaTeks else AbsensiColors.InkSoft, Modifier.weight(1f))
        }

        if (stat != null && stat.totalAbsensi > 0) {
            LinearProgressIndicator(
                progress = { stat.persenTersinkron / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = AbsensiColors.SuksesTeks,
                trackColor = AbsensiColors.Surface2,
            )
            Text("${stat.tersinkron} / ${stat.totalAbsensi} absensi tersinkron", style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft)
        }

        Text(
            "Referensi lokal: ${stat?.siswaLokal ?: 0} siswa · ${stat?.jadwalLokal ?: 0} jadwal · ${stat?.dispensasiLokal ?: 0} dispensasi",
            style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft
        )
        Text(
            "Sync terakhir: " + (stat?.syncTerakhir?.replace('T', ' ')?.take(19) ?: "belum pernah"),
            style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft
        )
        if (stat?.syncTerakhirStatus == "failed") {
            Text(
                "⚠ Siklus sync terakhir GAGAL: " + (stat.syncTerakhirError ?: "penyebab tidak tercatat"),
                style = MaterialTheme.typography.bodySmall, color = AbsensiColors.BahayaTeks
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm)) {
            Button(onClick = onSync, enabled = !state.sedangSync) {
                if (state.sedangSync) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Spasi.sm))
                }
                Text("Sync sekarang")
            }
            OutlinedButton(onClick = onRefresh) { Text("Muat ulang") }
        }

        Text("20 record terbaru", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
        if (state.recordTerbaru.isEmpty()) {
            Text("Belum ada absensi.", style = MaterialTheme.typography.bodyMedium, color = AbsensiColors.InkMuted)
        } else {
            KartuTabel {
                BarisTabel("Waktu", "Siswa", "Tipe", "Status", tebal = true)
                state.recordTerbaru.forEach { r -> BarisRecord(r) }
            }
        }
    }
}

@Composable
private fun BarisRecord(r: AbsensiLokal) {
    val (teks, warna) = when {
        r.synced == 1 -> "Tersinkron" to AbsensiColors.SuksesTeks
        r.sync_status == "gagal" -> "Gagal" to AbsensiColors.BahayaTeks
        else -> "Menunggu" to AbsensiColors.WarningTeks
    }
    BarisTabel(r.jam_aktual.take(8), r.siswa_id.toString(), r.type, teks, warnaKolom4 = warna)
}

// ---------------------------------------------------------------------------
// Jadwal
// ---------------------------------------------------------------------------

@Composable
private fun JadwalPane(
    state: AdminPanelUiState,
    onTambah: (String, String, String, String, String) -> Unit,
    onHapus: (String) -> Unit,
    onReset: () -> Unit,
) {
    var tanggal by remember { mutableStateOf(LocalDate.now().toString()) }
    var kelas by remember { mutableStateOf("") }
    var jamMasuk by remember { mutableStateOf("07:00") }
    var jamPulang by remember { mutableStateOf("15:00") }
    var alasan by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(Spasi.lg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spasi.md)
    ) {
        JudulPane("Jadwal", "Override lokal berlaku LANGSUNG untuk absensi offline (mendahului jadwal server).")

        Surface(
            color = AbsensiColors.Surface,
            shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                Text("Tambah override lokal", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                    OutlinedTextField(tanggal, { tanggal = it }, label = { Text("Tanggal") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(kelas, { kelas = it }, label = { Text("Kelas (kosong = semua)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                    OutlinedTextField(jamMasuk, { jamMasuk = it }, label = { Text("Jam masuk") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(jamPulang, { jamPulang = it }, label = { Text("Jam pulang") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(alasan, { alasan = it }, label = { Text("Alasan (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onTambah(tanggal, kelas, jamMasuk, jamPulang, alasan) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Simpan override")
                }
            }
        }

        Text("Override lokal (${state.overrideLokal.size})", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
        if (state.overrideLokal.isEmpty()) {
            Text("Belum ada override lokal.", style = MaterialTheme.typography.bodyMedium, color = AbsensiColors.InkMuted)
        } else {
            KartuTabel {
                BarisTabel("Tanggal", "Kelas", "Jam", "Status", tebal = true)
                state.overrideLokal.forEach { o -> BarisOverride(o, onHapus) }
            }
            OutlinedButton(onClick = onReset) { Text("Reset yang ditolak server") }
        }

        Text("Jadwal standar (cache server, ${state.jadwalStandar.size})", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
        if (state.jadwalStandar.isEmpty()) {
            Text("Belum ada jadwal ter-cache. Jalankan sinkronisasi.", style = MaterialTheme.typography.bodyMedium, color = AbsensiColors.InkMuted)
        } else {
            KartuTabel {
                BarisTabel("Kelas", "Tanggal", "Masuk", "Pulang", tebal = true)
                state.jadwalStandar.forEach { j -> BarisTabel(j.kelas, j.tanggal, j.jam_masuk.take(5), j.jam_pulang.take(5)) }
            }
        }
    }
}

@Composable
private fun BarisOverride(o: JadwalOverrideLokal, onHapus: (String) -> Unit) {
    val (teks, warna) = when (o.status_push) {
        "ok" -> "Di server" to AbsensiColors.SuksesTeks
        "ditolak" -> "Ditolak" to AbsensiColors.BahayaTeks
        else -> "Menunggu" to AbsensiColors.WarningTeks
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SelTabel(o.tanggal, Modifier.weight(1f))
        SelTabel(o.kelas ?: "semua", Modifier.weight(1f))
        SelTabel("${o.jam_masuk.take(5)}–${o.jam_pulang.take(5)}", Modifier.weight(1f))
        SelTabel(teks, Modifier.weight(1f), warna)
        IconButton(onClick = { onHapus(o.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Hapus", tint = AbsensiColors.InkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Data Siswa
// ---------------------------------------------------------------------------

@Composable
private fun DataSiswaPane(
    state: AdminPanelUiState,
    onTarikServer: () -> Unit,
    onMuatUlang: () -> Unit,
) {
    var cari by remember { mutableStateOf("") }
    val terfilter = remember(cari, state.siswa) {
        if (cari.isBlank()) state.siswa
        else state.siswa.filter { it.nama.contains(cari, true) || it.nis.contains(cari, true) || it.kelas.contains(cari, true) }
    }
    Column(Modifier.fillMaxSize().padding(Spasi.lg), verticalArrangement = Arrangement.spacedBy(Spasi.md)) {
        JudulPane("Data Siswa", "${state.siswa.size} siswa ter-cache di kiosk (matching offline).")

        Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onTarikServer, enabled = !state.sedangTarikSiswa) {
                if (state.sedangTarikSiswa) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Spasi.sm))
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spasi.sm))
                }
                Text("Tarik dari server")
            }
            OutlinedButton(onClick = onMuatUlang, enabled = !state.sedangTarikSiswa) { Text("Muat ulang") }
        }

        OutlinedTextField(cari, { cari = it }, label = { Text("Cari nama / NIS / kelas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (terfilter.isEmpty()) {
            Text("Tidak ada siswa.", style = MaterialTheme.typography.bodyMedium, color = AbsensiColors.InkMuted)
        } else {
            KartuTabel {
                BarisTabel("NIS", "Nama", "Kelas", "Wajah", tebal = true)
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(terfilter, key = { it.siswaId }) { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SelTabel(s.nis + if (s.lokal) " (lokal)" else "", Modifier.weight(1f))
                        SelTabel(s.nama, Modifier.weight(1.4f))
                        SelTabel(s.kelas, Modifier.weight(1f))
                        SelTabel(
                            if (s.terEnroll) "✓" else "—",
                            Modifier.weight(0.5f),
                            if (s.terEnroll) AbsensiColors.SuksesTeks else AbsensiColors.InkMuted
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pengaturan
// ---------------------------------------------------------------------------

@Composable
private fun PengaturanPane(
    state: AdminPanelUiState,
    onSaveUrl: (String) -> Unit,
    onLensa: (Boolean) -> Unit,
    onSaveFaceKey: (String) -> Unit,
    onTesFaceKey: () -> Unit,
    onSaveAmbang: (Float) -> Unit,
    onHapusKredensial: () -> Unit,
) {
    var url by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var faceKey by remember(state.faceKey) { mutableStateOf(state.faceKey) }
    var ambang by remember(state.ambangJarak) { mutableFloatStateOf(state.ambangJarak) }
    var konfirmasiHapus by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(Spasi.lg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spasi.md)
    ) {
        JudulPane("Pengaturan", null)

        Surface(color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                Text("URL server", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("Kosong = default") },
                    placeholder = { Text(state.serverUrlDefault) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onSaveUrl(url) }, modifier = Modifier.align(Alignment.End)) { Text("Simpan URL") }
            }
        }

        Surface(color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                Text("Face Encryption Key", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
                OutlinedTextField(
                    faceKey, { faceKey = it },
                    label = { Text("FACE_ENCRYPTION_KEY server") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                val statusKey = if (state.faceKey.isBlank()) "belum diisi (dari local.properties / field ini)"
                else "tersimpan · ${state.faceKey.length} karakter"
                Text(
                    "Status: $statusKey. Fernet key dari .env server (44 karakter). " +
                        "Hilang tiap uninstall — untuk permanen isi FACE_ENCRYPTION_KEY di local.properties lalu rebuild.",
                    style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm), modifier = Modifier.align(Alignment.End)) {
                    OutlinedButton(onClick = onTesFaceKey) { Text("Tes Face Key") }
                    Button(onClick = { onSaveFaceKey(faceKey) }) { Text("Simpan Key") }
                }
            }
        }

        Surface(color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                Text("Ambang match wajah: ${"%.2f".format(ambang)}", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
                Slider(value = ambang, onValueChange = { ambang = it }, valueRange = 0.20f..0.80f, steps = 59)
                Text(
                    "Lebih besar = lebih longgar (wajah asli tidak lagi 'tidak dikenali'). " +
                        "Lebih kecil = lebih ketat. Default 0.35. Kartu 'Tidak dikenali' menampilkan jarak terdekat — pilih ambang sedikit di atasnya.",
                    style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft,
                )
                Button(onClick = { onSaveAmbang(ambang) }, modifier = Modifier.align(Alignment.End)) { Text("Simpan Ambang") }
            }
        }

        Surface(color = AbsensiColors.Surface, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(Spasi.md).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Kamera", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.InkMuted)
                    Text(if (state.lensaDepan) "Depan (selfie)" else "Belakang", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = state.lensaDepan, onCheckedChange = onLensa)
            }
        }

        Surface(color = AbsensiColors.BahayaBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                Text("Zona berbahaya", style = MaterialTheme.typography.labelMedium, color = AbsensiColors.BahayaTeks)
                Text("Hapus device_id + API key. Perlu daftar ulang.", style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkSoft)
                if (konfirmasiHapus) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spasi.sm)) {
                        Button(
                            onClick = onHapusKredensial,
                            colors = ButtonDefaults.buttonColors(containerColor = AbsensiColors.BahayaTeks, contentColor = AbsensiColors.Bg)
                        ) { Text("Ya, hapus") }
                        OutlinedButton(onClick = { konfirmasiHapus = false }) { Text("Batal") }
                    }
                } else {
                    OutlinedButton(onClick = { konfirmasiHapus = true }) { Text("Hapus kredensial") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dashboard Web (Fase C)
// ---------------------------------------------------------------------------

@Composable
private fun WebLinksPane(serverUrl: String) {
    val context = LocalContext.current
    val dashboardUrl = remember(serverUrl) {
        serverUrl.replaceFirst("absen.", "front.").trimEnd('/')
    }
    fun buka(path: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$dashboardUrl$path")))
        }
    }
    Column(Modifier.fillMaxSize().padding(Spasi.lg), verticalArrangement = Arrangement.spacedBy(Spasi.md)) {
        JudulPane("Dashboard Web", "Data guru & laporan lengkap dikelola di dashboard web sekolah.")
        Button(onClick = { buka("/dashboard/guru") }, modifier = Modifier.fillMaxWidth()) { Text("Kelola Data Guru") }
        Button(onClick = { buka("/dashboard/laporan") }, modifier = Modifier.fillMaxWidth()) { Text("Lihat Laporan") }
        Button(onClick = { buka("/dashboard/jadwal") }, modifier = Modifier.fillMaxWidth()) { Text("Pengaturan Jadwal Lengkap") }
        Text(dashboardUrl, style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkMuted)
    }
}

// ---------------------------------------------------------------------------
// Komponen bersama
// ---------------------------------------------------------------------------

@Composable
internal fun JudulPane(judul: String, sub: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spasi.xs)) {
        Text(judul, style = MaterialTheme.typography.headlineSmall)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = AbsensiColors.InkSoft)
    }
}

@Composable
private fun KartuStat(nilai: String, label: String, aksen: Color, modifier: Modifier = Modifier) {
    Surface(
        color = AbsensiColors.Surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = modifier
    ) {
        Column(Modifier.padding(Spasi.md), verticalArrangement = Arrangement.spacedBy(Spasi.xs)) {
            Text(nilai, style = MaterialTheme.typography.headlineMedium, color = aksen)
            Text(label, style = MaterialTheme.typography.bodySmall, color = AbsensiColors.InkMuted)
        }
    }
}

@Composable
private fun KartuTabel(isi: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = AbsensiColors.Surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spasi.md), content = isi)
    }
}

@Composable
private fun BarisTabel(
    a: String, b: String, c: String, d: String,
    tebal: Boolean = false,
    warnaKolom4: Color = AbsensiColors.Ink,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SelTabel(a, Modifier.weight(1f), tebal = tebal)
        SelTabel(b, Modifier.weight(1f), tebal = tebal)
        SelTabel(c, Modifier.weight(1f), tebal = tebal)
        SelTabel(d, Modifier.weight(1f), if (tebal) AbsensiColors.InkMuted else warnaKolom4, tebal = tebal)
    }
}

@Composable
private fun SelTabel(teks: String, modifier: Modifier = Modifier, warna: Color = AbsensiColors.Ink, tebal: Boolean = false) {
    Text(
        teks,
        modifier = modifier,
        color = warna,
        maxLines = 1,
        style = if (tebal) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun PesanBar(pesan: String, error: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (error) AbsensiColors.BahayaBg else AbsensiColors.SuksesBg,
        contentColor = if (error) AbsensiColors.BahayaTeks else AbsensiColors.SuksesTeks,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.padding(Spasi.md).fillMaxWidth()
    ) {
        Row(Modifier.padding(Spasi.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pesan, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    }
}
