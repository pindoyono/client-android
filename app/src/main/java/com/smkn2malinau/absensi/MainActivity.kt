package com.smkn2malinau.absensi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.ui.KioskScreen
import com.smkn2malinau.absensi.ui.KioskUiState
import com.smkn2malinau.absensi.ui.StatusJaringan

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentialManager = CredentialManager(this)
        val onSiteTestingSelesai = credentialManager.isOnSiteTestingSelesai()

        setContent {
            AbsensiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KioskScreen(
                        state = KioskUiState(
                            statusJaringan = StatusJaringan.ONLINE,
                            jamSekarang = "14:32",
                            onSiteTestingSelesai = onSiteTestingSelesai
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AbsensiTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
