package com.smkn2malinau.absensi.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smkn2malinau.absensi.ui.theme.AbsensiColors
import com.smkn2malinau.absensi.ui.theme.PaletHasil
import com.smkn2malinau.absensi.ui.theme.Spasi

/**
 * State UI untuk layar kiosk.
 */
data class KioskUiState(
    val statusJaringan: StatusJaringan = StatusJaringan.SINKRON_TERTUNDA,
    val jamSekarang: String = "14:32",
    val onSiteTestingSelesai: Boolean = false,
    val hasilTerakhir: HasilScan? = null,
    /** Status bar sinkronisasi + jam masuk/pulang — setara header kiosk Windows. */
    val ringkasanSync: RingkasanSyncUi? = null,
    val jadwalMasuk: String? = null,
    val jadwalPulang: String? = null,
    /** Jadwal di header berasal dari override (bukan jadwal standar). */
    val jadwalOverride: Boolean = false,
    /** Opsi jadwal per kelas (dropdown header). Kosong / 1 entri = tanpa dropdown. */
    val jadwalOpsiKelas: List<JadwalOpsiKelasUi> = emptyList(),
    /** Kelas yang sedang dipilih di dropdown header. "" = Umum (default). */
    val jadwalKelasDipilih: String = "",
    val kesegaran: KesegaranUi = KesegaranUi.TIDAK_DIKETAHUI,
    val dataBasi: List<String> = emptyList(),
    /** Baris "Nama · Masuk/Pulang · keterangan" dari 5 absensi terakhir — daftar persisten di kiosk. */
    val riwayatAbsen: List<String> = emptyList(),
    /** Geofencing (opt-in per device) — false = kiosk diblokir total, lihat KartuLokasiTidakValid. */
    val lokasiValid: Boolean = true,
    val lokasiAlasan: String? = null,
    /** Jarak (meter) ke titik acuan server saat pengecekan terakhir — null = lokasi belum diatur. */
    val lokasiJarakMeter: Double? = null,
    /** Admin sudah pasang titik acuan geofencing untuk device ini atau belum — indikator ikon header. */
    val lokasiDikonfigurasi: Boolean = false,
    /** Sync manual dari header sedang berjalan — dipakai tombol sync untuk spinner. */
    val sedangSync: Boolean = false,
    /** Instruksi anti-spoof yang sedang aktif ("Kedipkan mata", "Verifikasi wajah…") — null = tak ada. */
    val instruksiLiveness: String? = null,
    /** Nama lokasi kiosk (dari server, disegarkan tiap sync). "" = belum diketahui. */
    val namaLokasi: String = "",
)

/** Baris "Sync: 04/09 00:19 · 0 antre, 128 wajah, 11 jadwal". */
data class RingkasanSyncUi(
    val waktuTeks: String,
    val antreKirim: Int,
    val jumlahWajah: Int,
    val jumlahJadwal: Int,
)

enum class KesegaranUi { SEGAR, BASI, TIDAK_DIKETAHUI }

/** Opsi di dropdown pemilih kelas pada header kiosk. */
data class JadwalOpsiKelasUi(
    val kelas: String,       // "" = umum
    val label: String,       // "Umum" atau nama kelas
    val jamMasuk: String,    // "07:00"
    val jamPulang: String,   // "15:00"
    val override: Boolean,
)

/**
 * Status pil kiri-atas. Ditentukan gabungan jaringan + hasil siklus sync terakhir
 * (bukan cuma konektivitas) — sesuai perilaku kiosk Windows yang cek `/health`
 * di awal tiap siklus.
 */
enum class StatusJaringan {
    /** Jaringan ada DAN siklus sync terakhir sukses. */
    ONLINE,
    /** Jaringan ada tapi siklus sync terakhir gagal / belum pernah jalan. */
    SINKRON_TERTUNDA,
    /** Tidak ada jaringan — absensi disimpan lokal. */
    OFFLINE,
}

data class HasilScan(
    val status: StatusHasil,
    val nama: String = "",
    val kelas: String = "",
    val nis: String = "",
    val pesan: String = "",
    /** Baris kecil di bawah — diagnostik (mis. jarak match), tidak menggantikan judul. */
    val diagnostik: String = "",
)

enum class StatusHasil {
    BERHASIL_TEPAT_WAKTU,
    BERHASIL_TERLAMBAT,
    BERHASIL_PULANG_DISPENSASI,
    DITOLAK_SUDAH_ABSEN,
    DITOLAK_BELUM_WAKTUNYA,
    WAJAH_TIDAK_DIKENALI,
    OFFLINE
}

@Composable
fun KioskScreen(
    state: KioskUiState,
    kameraSiap: Boolean = true,
    onOpenAdmin: () -> Unit = {},
    onSyncSekarang: () -> Unit = {},
    onPilihJadwalKelas: (String) -> Unit = {},
    cameraContent: @Composable () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(AbsensiColors.Bg)) {
        cameraContent()

        // Scrim supaya teks tetap terbaca di atas preview kamera.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to AbsensiColors.Bg.copy(alpha = if (kameraSiap) 0.72f else 1f),
                        0.35f to AbsensiColors.Bg.copy(alpha = if (kameraSiap) 0.30f else 1f),
                        0.7f to AbsensiColors.Bg.copy(alpha = if (kameraSiap) 0.45f else 1f),
                        1f to AbsensiColors.Bg.copy(alpha = if (kameraSiap) 0.85f else 1f),
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            BarisAtas(state, onOpenAdmin, onSyncSekarang, onPilihJadwalKelas)

            if (!state.lokasiValid) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(Spasi.lg),
                    contentAlignment = Alignment.Center
                ) {
                    KartuLokasiTidakValid(state.lokasiAlasan)
                }
                return@Column
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Spasi.lg),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = state.hasilTerakhir,
                    animationSpec = tween(220),
                    label = "hasil-scan"
                ) { hasil ->
                    if (hasil == null) KartuIdle(kameraSiap) else KartuHasil(hasil)
                }
                if (state.hasilTerakhir == null && state.instruksiLiveness != null) {
                    ChipInstruksi(state.instruksiLiveness)
                }
            }

            if (state.riwayatAbsen.isNotEmpty()) {
                DaftarRiwayatAbsen(state.riwayatAbsen)
            }

            Text(
                text = "Arahkan wajah untuk absen berikutnya",
                color = AbsensiColors.InkMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spasi.lg, start = Spasi.lg, end = Spasi.lg)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarisAtas(
    state: KioskUiState,
    onOpenAdmin: () -> Unit,
    onSyncSekarang: () -> Unit,
    onPilihJadwalKelas: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spasi.lg, vertical = Spasi.md),
        verticalArrangement = Arrangement.spacedBy(Spasi.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spasi.sm),
        ) {
            // KIRI — menyusut/ellipsis kalau sempit (tombol admin tak boleh tergeser).
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PilStatusJaringan(state.statusJaringan)
                IconButton(onClick = onSyncSekarang, enabled = !state.sedangSync, modifier = Modifier.size(24.dp)) {
                    if (state.sedangSync) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = AbsensiColors.InkSoft)
                    } else {
                        Icon(Icons.Default.Refresh, "Sync sekarang", tint = AbsensiColors.InkSoft, modifier = Modifier.size(15.dp))
                    }
                }
                if (state.namaLokasi.isNotBlank()) {
                    Text(
                        text = "📍 ${state.namaLokasi}",
                        color = if (state.lokasiDikonfigurasi) AbsensiColors.InkSoft else AbsensiColors.InkMuted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = if (state.lokasiDikonfigurasi) "Lokasi kiosk sudah diatur" else "Lokasi kiosk belum diatur",
                        tint = if (state.lokasiDikonfigurasi) AbsensiColors.SuksesTeks else AbsensiColors.InkMuted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            // KANAN — lebar natural, selalu terlihat.
            Text(
                text = state.jamSekarang,
                color = AbsensiColors.Ink,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onOpenAdmin, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, "Buka menu admin", tint = AbsensiColors.InkSoft, modifier = Modifier.size(20.dp))
            }
        }

        state.ringkasanSync?.let { r ->
            Text(
                text = "Sync: ${r.waktuTeks} · ${r.antreKirim} antre, ${r.jumlahWajah} wajah, ${r.jumlahJadwal} jadwal",
                color = AbsensiColors.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spasi.sm),
            verticalArrangement = Arrangement.spacedBy(Spasi.xs),
        ) {
            ChipJadwal(
                masuk = state.jadwalMasuk,
                pulang = state.jadwalPulang,
                dariOverride = state.jadwalOverride,
                opsiKelas = state.jadwalOpsiKelas,
                kelasDipilih = state.jadwalKelasDipilih,
                onPilihKelas = onPilihJadwalKelas,
            )
            when (state.kesegaran) {
                KesegaranUi.SEGAR ->
                    ChipInfo("✓ Data segar", AbsensiColors.SuksesTeks, AbsensiColors.SuksesBg)
                KesegaranUi.BASI ->
                    ChipInfo(
                        "⚠ ${state.dataBasi.joinToString(" & ").ifEmpty { "Data" }} basi",
                        AbsensiColors.WarningTeks, AbsensiColors.WarningBg
                    )
                KesegaranUi.TIDAK_DIKETAHUI -> Unit
            }
            state.lokasiJarakMeter?.let { jarak ->
                val warna = if (state.lokasiValid) AbsensiColors.SuksesTeks else AbsensiColors.BahayaTeks
                val latar = if (state.lokasiValid) AbsensiColors.SuksesBg else AbsensiColors.BahayaBg
                ChipInfo("📍 ${formatJarak(jarak)} dari lokasi", warna, latar)
            }
            if (!state.onSiteTestingSelesai) {
                ChipInfo("MODE TESTING · TIDAK DISIMPAN", AbsensiColors.WarningTeks, AbsensiColors.WarningBg)
            }
        }
    }
}

/** "12m" untuk jarak &lt; 1km, "1.2km" di atasnya — chip geofencing di header. */
private fun formatJarak(meter: Double): String =
    if (meter < 1000) "${meter.toInt()}m" else "%.1fkm".format(meter / 1000)

@Composable
private fun ChipJadwal(
    masuk: String?,
    pulang: String?,
    dariOverride: Boolean = false,
    opsiKelas: List<JadwalOpsiKelasUi> = emptyList(),
    kelasDipilih: String = "",
    onPilihKelas: (String) -> Unit = {},
) {
    val warna = if (dariOverride) AbsensiColors.WarningTeks else AbsensiColors.Border
    val warnaTeks = if (dariOverride) AbsensiColors.WarningTeks else AbsensiColors.Ink
    // Dropdown hanya bila ada > 1 jadwal (mis. jadwal khusus per kelas).
    val adaDropdown = opsiKelas.size > 1
    var menuTerbuka by remember { mutableStateOf(false) }
    val labelKelas = opsiKelas.firstOrNull { it.kelas == kelasDipilih }?.label
        ?: opsiKelas.firstOrNull()?.label ?: "Umum"

    Surface(
        color = AbsensiColors.Surface2,
        contentColor = AbsensiColors.Ink,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, warna)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(if (adaDropdown) Modifier.clickable { menuTerbuka = true } else Modifier)
                .padding(horizontal = Spasi.md, vertical = 6.dp),
        ) {
            Text(
                text = buildString {
                    append("Masuk: ${masuk ?: "--:--"}   Pulang: ${pulang ?: "--:--"}")
                    if (dariOverride) append("  · override")
                    if (adaDropdown) append("  · $labelKelas")
                },
                style = MaterialTheme.typography.labelLarge,
                color = warnaTeks,
            )
            if (adaDropdown) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Pilih kelas",
                    tint = warnaTeks,
                    modifier = Modifier.size(18.dp),
                )
                DropdownMenu(expanded = menuTerbuka, onDismissRequest = { menuTerbuka = false }) {
                    opsiKelas.forEach { o ->
                        DropdownMenuItem(
                            text = { Text("${o.label} — ${o.jamMasuk}/${o.jamPulang}") },
                            onClick = { onPilihKelas(o.kelas); menuTerbuka = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PilStatusJaringan(status: StatusJaringan) {
    val (warna, teks) = when (status) {
        StatusJaringan.ONLINE -> AbsensiColors.SuksesTeks to "Tersinkron"
        StatusJaringan.SINKRON_TERTUNDA -> AbsensiColors.WarningTeks to "Belum sinkron"
        StatusJaringan.OFFLINE -> AbsensiColors.NetralTeks to "Offline"
    }
    val warnaAnim by animateColorAsState(warna, tween(300), label = "dot")
    Surface(
        color = AbsensiColors.Surface.copy(alpha = 0.7f),
        contentColor = AbsensiColors.InkSoft,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spasi.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(warnaAnim))
            Text(teks, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChipInfo(teks: String, aksen: Color, latar: Color) {
    Surface(color = latar, contentColor = aksen, shape = MaterialTheme.shapes.small) {
        Text(
            teks,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spasi.md, vertical = 6.dp)
        )
    }
}

/** Warna teks bergantian untuk daftar nama — tanpa latar, murni teks berwarna. */
private val WarnaRiwayat = listOf(
    Color(0xFF60A5FA), // biru
    Color(0xFF4ADE80), // hijau
    Color(0xFFFBBF24), // kuning
    Color(0xFFF472B6), // pink
    Color(0xFFA78BFA), // ungu
)

/** Daftar 5 absensi terakhir — nama + masuk/pulang + keterangan, warna-warni, tanpa background. Selalu tampil. */
@Composable
private fun DaftarRiwayatAbsen(baris: List<String>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spasi.sm)
    ) {
        baris.take(5).forEachIndexed { i, teks ->
            Text(
                text = teks,
                color = WarnaRiwayat[i % WarnaRiwayat.size],
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Kiosk diblokir total — geofencing gagal (di luar radius / GPS palsu / lokasi tak tersedia). */
@Composable
private fun KartuLokasiTidakValid(alasan: String?) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = AbsensiColors.BahayaBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.BahayaTeks),
        modifier = Modifier.widthIn(max = 400.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spasi.sm),
            modifier = Modifier.padding(horizontal = Spasi.lg, vertical = Spasi.lg)
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(AbsensiColors.BahayaBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AbsensiColors.BahayaTeks, modifier = Modifier.size(36.dp))
            }
            Text(
                "Lokasi Tidak Valid",
                color = AbsensiColors.BahayaTeks,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                alasan ?: "Kiosk ini berada di luar lokasi yang diizinkan.",
                color = AbsensiColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "Absensi dinonaktifkan sampai lokasi terverifikasi kembali. Hubungi admin sekolah.",
                color = AbsensiColors.InkSoft,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun KartuIdle(kameraSiap: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spasi.md)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(AbsensiColors.Surface.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Face,
                contentDescription = null,
                tint = AbsensiColors.InkSoft,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            if (kameraSiap) "Arahkan wajah ke kamera" else "Menunggu izin kamera…",
            color = AbsensiColors.InkSoft,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChipInstruksi(teks: String) {
    Surface(
        color = AbsensiColors.Surface2,
        contentColor = AbsensiColors.Ink,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Aksen),
        modifier = Modifier.padding(top = Spasi.xl),
    ) {
        Text(
            teks,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spasi.lg, vertical = Spasi.md),
        )
    }
}

@Composable
private fun KartuHasil(hasil: HasilScan) {
    val palet = paletUntuk(hasil.status)
    val ikon = ikonUntuk(hasil.status)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = AbsensiColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbsensiColors.Border),
        modifier = Modifier.widthIn(max = 400.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spasi.sm),
            modifier = Modifier.padding(horizontal = Spasi.lg, vertical = Spasi.lg)
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(palet.latar),
                contentAlignment = Alignment.Center
            ) {
                Icon(ikon, contentDescription = null, tint = palet.aksen, modifier = Modifier.size(32.dp))
            }

            Text(palet.label.uppercase(), color = palet.aksen, style = MaterialTheme.typography.labelSmall)

            Text(
                hasil.pesan.ifEmpty { palet.label },
                color = AbsensiColors.Ink,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            if (hasil.diagnostik.isNotEmpty()) {
                Text(
                    hasil.diagnostik,
                    color = AbsensiColors.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }

            if (hasil.nama.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        Modifier
                            .padding(top = 2.dp, bottom = 2.dp)
                            .width(28.dp)
                            .height(2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(AbsensiColors.Border)
                    )
                    Text(hasil.nama, color = AbsensiColors.Ink, style = MaterialTheme.typography.titleSmall)
                    val detail = listOf(hasil.kelas, hasil.nis).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (detail.isNotEmpty()) {
                        Text(detail, color = AbsensiColors.InkSoft, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun paletUntuk(status: StatusHasil): PaletHasil = when (status) {
    StatusHasil.BERHASIL_TEPAT_WAKTU ->
        PaletHasil(AbsensiColors.SuksesTeks, AbsensiColors.SuksesBg, "Tepat waktu")
    StatusHasil.BERHASIL_TERLAMBAT ->
        PaletHasil(AbsensiColors.WarningTeks, AbsensiColors.WarningBg, "Terlambat")
    StatusHasil.BERHASIL_PULANG_DISPENSASI ->
        PaletHasil(AbsensiColors.WarningTeks, AbsensiColors.WarningBg, "Pulang dengan izin")
    StatusHasil.DITOLAK_SUDAH_ABSEN ->
        PaletHasil(AbsensiColors.BahayaTeks, AbsensiColors.BahayaBg, "Sudah absen")
    StatusHasil.DITOLAK_BELUM_WAKTUNYA ->
        PaletHasil(AbsensiColors.WarningTeks, AbsensiColors.WarningBg, "Belum waktunya")
    StatusHasil.WAJAH_TIDAK_DIKENALI ->
        PaletHasil(AbsensiColors.NetralTeks, AbsensiColors.NetralBg, "Tidak dikenali")
    StatusHasil.OFFLINE ->
        PaletHasil(AbsensiColors.NetralTeks, AbsensiColors.NetralBg, "Offline")
}

private fun ikonUntuk(status: StatusHasil): ImageVector = when (status) {
    StatusHasil.BERHASIL_TEPAT_WAKTU,
    StatusHasil.BERHASIL_TERLAMBAT,
    StatusHasil.BERHASIL_PULANG_DISPENSASI -> Icons.Default.CheckCircle
    StatusHasil.DITOLAK_SUDAH_ABSEN -> Icons.Default.Info
    StatusHasil.DITOLAK_BELUM_WAKTUNYA, StatusHasil.OFFLINE -> Icons.Default.Warning
    StatusHasil.WAJAH_TIDAK_DIKENALI -> Icons.Default.Face
}
