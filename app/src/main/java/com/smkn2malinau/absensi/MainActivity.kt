package com.smkn2malinau.absensi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.ui.AdminScreen
import com.smkn2malinau.absensi.ui.CameraView
import com.smkn2malinau.absensi.ui.KioskScreen
import com.smkn2malinau.absensi.ui.KioskViewModel
import com.smkn2malinau.absensi.ui.KioskViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentialManager = CredentialManager(this)

        setContent {
            var showAdmin by remember {
                mutableStateOf(credentialManager.getDeviceId() == null || credentialManager.getApiKey() == null)
            }

            AbsensiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showAdmin) {
                        AdminScreen(
                            credentialManager = credentialManager,
                            onSaveSuccess = { showAdmin = false }
                        )
                    } else {
                        KioskRoot()
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
private fun KioskRoot() {
    val context = LocalContext.current
    val viewModel: KioskViewModel = viewModel(factory = KioskViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()

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
        viewModel.refreshModeTesting()
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    KioskScreen(state = state) {
        if (hasCameraPermission) {
            CameraView(
                modifier = Modifier.fillMaxSize(),
                onFrameAnalysis = viewModel::onFrameCaptured
            )
        }
    }
}

@Composable
fun AbsensiTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
