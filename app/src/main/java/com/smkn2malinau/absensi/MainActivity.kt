package com.smkn2malinau.absensi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.auth.Fitur
import com.smkn2malinau.absensi.auth.SesiPengguna
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.sync.SyncWorker
import com.smkn2malinau.absensi.ui.AdminScreen
import com.smkn2malinau.absensi.ui.CameraView
import com.smkn2malinau.absensi.ui.KioskScreen
import com.smkn2malinau.absensi.ui.KioskViewModel
import com.smkn2malinau.absensi.ui.KioskViewModelFactory
import com.smkn2malinau.absensi.ui.EnrollmentScreen
import com.smkn2malinau.absensi.ui.admin.AdminPanelScreen
import com.smkn2malinau.absensi.ui.auth.LoginScreen
import com.smkn2malinau.absensi.ui.siswa.RiwayatSiswaScreen
import com.smkn2malinau.absensi.ui.theme.AbsensiTheme

private enum class Layar { SETUP, KIOSK, ENROLLMENT, LOGIN, PANEL_ADMIN, RIWAYAT_SISWA }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentialManager = CredentialManager(this)

        setContent {
            var layar by remember {
                mutableStateOf(if (credentialManager.hasCredentials()) Layar.KIOSK else Layar.SETUP)
            }
            var sesi by remember { mutableStateOf<SesiPengguna?>(null) }

            AbsensiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (layar) {
                        Layar.SETUP -> AdminScreen(
                            onSaveSuccess = { layar = Layar.KIOSK },
                            onOpenEnrollment = { layar = Layar.ENROLLMENT },
                        )
                        Layar.KIOSK -> KioskRoot(onOpenAdmin = { layar = Layar.LOGIN })
                        Layar.ENROLLMENT -> EnrollmentScreen(
                            onBack = { layar = if (credentialManager.hasCredentials()) Layar.KIOSK else Layar.SETUP },
                            onSelesai = { layar = Layar.KIOSK },
                        )
                        Layar.LOGIN -> LoginScreen(
                            onLolos = { s ->
                                sesi = s
                                layar = if (s.boleh(Fitur.PANEL_ADMIN)) Layar.PANEL_ADMIN
                                else Layar.RIWAYAT_SISWA
                            },
                            onBatal = {
                                credentialManager.clearSesi(); sesi = null; layar = Layar.KIOSK
                            },
                        )
                        Layar.PANEL_ADMIN -> AdminPanelScreen(
                            sesi = sesi,
                            onTutup = {
                                credentialManager.clearSesi(); sesi = null; layar = Layar.KIOSK
                            },
                            onKredensialDihapus = {
                                credentialManager.clearSesi(); sesi = null; layar = Layar.SETUP
                            },
                        )
                        Layar.RIWAYAT_SISWA -> RiwayatSiswaScreen(
                            sesi = sesi,
                            onTutup = {
                                credentialManager.clearSesi(); sesi = null; layar = Layar.KIOSK
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Root kiosk — menyambungkan KioskViewModel ke KioskScreen + CameraView (PRD bagian 4).
 */
@Composable
private fun KioskRoot(onOpenAdmin: () -> Unit) {
    val context = LocalContext.current
    val viewModel: KioskViewModel = viewModel(factory = KioskViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()
    val credentialManager = remember { CredentialManager(context) }
    val lensaDepan = remember { credentialManager.lensaKameraDepan() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // Geofencing (opt-in per device, lihat location/LocationChecker.kt) butuh izin
    // lokasi supaya SyncService bisa benar-benar cek posisi kiosk. Sebelumnya
    // hanya dicek pasif (checkSelfPermission) tanpa pernah diminta di layar
    // kiosk — kalau belum pernah diberikan, kiosk terus dianggap "lokasi tidak
    // tersedia" (fail-closed) selamanya tanpa admin pernah melihat dialog izin.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val lokasiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        // Begitu izin baru diberikan, langsung picu cek lokasi — jangan tunggu
        // siklus sync periodik (bisa sampai 15 menit) untuk membuka blokir kiosk.
        if (granted) SyncWorker.enqueueSekali(context, paksa = true)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshModeTesting()
        // Picu satu siklus sync saat kiosk dibuka supaya status "tersinkron"
        // & cache (wajah/jadwal) cepat terisi, tidak menunggu worker periodik.
        SyncWorker.enqueueSekali(context)
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (!hasLocationPermission) {
            lokasiPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    KioskScreen(
        state = state,
        kameraSiap = hasCameraPermission,
        onOpenAdmin = onOpenAdmin,
        onSyncSekarang = viewModel::syncSekarang
    ) {
        if (hasCameraPermission) {
            CameraView(
                modifier = Modifier.fillMaxSize(),
                lensDepan = lensaDepan,
                onFrameAnalysis = viewModel::onFrameCaptured
            )
        }
    }
}
