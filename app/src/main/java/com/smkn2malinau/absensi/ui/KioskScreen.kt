package com.smkn2malinau.absensi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smkn2malinau.absensi.ui.theme.AbsensiColors

/**
 * State UI untuk layar kiosk.
 */
data class KioskUiState(
    val statusJaringan: StatusJaringan = StatusJaringan.ONLINE,
    val jamSekarang: String = "14:32",
    val onSiteTestingSelesai: Boolean = false,
    val kesegaranBermasalah: Boolean = false,
    val hasilTerakhir: HasilScan? = null
)

enum class StatusJaringan { ONLINE, OFFLINE }

data class HasilScan(
    val status: StatusHasil,
    val nama: String = "",
    val kelas: String = "",
    val nis: String = "",
    val pesan: String = ""
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
    cameraContent: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AbsensiColors.Bg)
    ) {
        HeaderBar(status = state.statusJaringan, jam = state.jamSekarang)
        if (!state.onSiteTestingSelesai) ModeTestingBanner()
        if (state.kesegaranBermasalah) KesegaranDataBadge(state)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            cameraContent()
            HasilScanCard(state.hasilTerakhir)
        }
        FooterHint()
    }
}

@Composable
private fun HeaderBar(status: StatusJaringan, jam: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AbsensiColors.Surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (dotColor, text) = when (status) {
            StatusJaringan.ONLINE -> Pair(AbsensiColors.SuksesTeks, "Online · tersinkron")
            StatusJaringan.OFFLINE -> Pair(AbsensiColors.NetralTeks, "Offline · disimpan lokal")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = AbsensiColors.InkSoft, fontSize = 16.sp)
        }
        Text(jam, color = AbsensiColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModeTestingBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AbsensiColors.WarningBg)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "MODE TESTING",
            color = AbsensiColors.WarningTeks,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KesegaranDataBadge(state: KioskUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AbsensiColors.WarningBg)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Data mulai basi — sinkron segera",
            color = AbsensiColors.WarningTeks,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HasilScanCard(hasil: HasilScan?) {
    if (hasil == null) {
        Text(
            "Arahkan wajah ke kamera",
            color = AbsensiColors.InkSoft,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        return
    }

    val (bgColor, textColor, label) = when (hasil.status) {
        StatusHasil.BERHASIL_TEPAT_WAKTU -> Triple(AbsensiColors.SuksesBg, AbsensiColors.SuksesTeks, "Tepat waktu")
        StatusHasil.BERHASIL_TERLAMBAT -> Triple(AbsensiColors.WarningBg, AbsensiColors.WarningTeks, "Terlambat")
        StatusHasil.BERHASIL_PULANG_DISPENSASI -> Triple(AbsensiColors.WarningBg, AbsensiColors.WarningTeks, "Pulang dengan izin")
        StatusHasil.DITOLAK_SUDAH_ABSEN -> Triple(AbsensiColors.BahayaBg, AbsensiColors.BahayaTeks, "Ditolak")
        StatusHasil.DITOLAK_BELUM_WAKTUNYA -> Triple(AbsensiColors.WarningBg, AbsensiColors.WarningTeks, "Belum waktunya")
        StatusHasil.WAJAH_TIDAK_DIKENALI -> Triple(AbsensiColors.NetralBg, AbsensiColors.NetralTeks, "Tidak dikenali")
        StatusHasil.OFFLINE -> Triple(AbsensiColors.NetralBg, AbsensiColors.NetralTeks, "Offline")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(AbsensiColors.Surface2),
            contentAlignment = Alignment.Center
        ) {
            Text("📷", fontSize = 48.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            hasil.pesan.ifEmpty { label },
            color = textColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (hasil.nama.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(hasil.nama, color = AbsensiColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                "${hasil.kelas} · ${hasil.nis}",
                color = AbsensiColors.InkSoft,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun FooterHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AbsensiColors.Surface)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Arahkan wajah untuk berikutnya",
            color = AbsensiColors.InkMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
