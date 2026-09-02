package com.smkn2malinau.absensi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smkn2malinau.absensi.security.CredentialManager

/**
 * Layar Admin/Setup — Tema Terang (PRD bagian 6.5).
 * Digunakan untuk konfigurasi device_id dan api_key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    credentialManager: CredentialManager,
    onSaveSuccess: () -> Unit
) {
    var deviceId by remember { mutableStateOf(credentialManager.getDeviceId() ?: "") }
    var apiKey by remember { mutableStateOf(credentialManager.getApiKey() ?: "") }
    var isTestingMode by remember { mutableStateOf(!credentialManager.isOnSiteTestingSelesai()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Device Kiosk", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = { Text("Device ID") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Device API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isTestingMode,
                    onCheckedChange = { isTestingMode = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mode Testing Aktif (PRD Bagian 10)")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    credentialManager.saveDeviceId(deviceId)
                    credentialManager.saveApiKey(apiKey)
                    credentialManager.setOnSiteTestingSelesai(!isTestingMode)
                    onSaveSuccess()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Simpan Konfigurasi", fontSize = 18.sp)
            }
        }
    }
}
