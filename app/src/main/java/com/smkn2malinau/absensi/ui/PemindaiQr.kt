package com.smkn2malinau.absensi.ui

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.smkn2malinau.absensi.ui.theme.Spasi
import java.util.concurrent.Executors

/**
 * Pemindai QR "QR Setup" device — CameraX (kamera belakang) + ML Kit barcode.
 * Memanggil [onHasil] sekali dengan isi QR pertama yang terbaca, lalu berhenti.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun PemindaiQr(
    onHasil: (String) -> Unit,
    onBatal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var sudahKirim by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { ia ->
                            ia.setAnalyzer(executor) { proxy ->
                                val media = proxy.image
                                if (media == null || sudahKirim) {
                                    proxy.close(); return@setAnalyzer
                                }
                                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                scanner.process(input)
                                    .addOnSuccessListener { barcodes ->
                                        val isi = barcodes.firstOrNull {
                                            it.format == Barcode.FORMAT_QR_CODE
                                        }?.rawValue
                                        if (!isi.isNullOrBlank() && !sudahKirim) {
                                            sudahKirim = true
                                            ContextCompat.getMainExecutor(context).execute { onHasil(isi) }
                                        }
                                    }
                                    .addOnFailureListener { e -> Log.w("PemindaiQr", "scan gagal", e) }
                                    .addOnCompleteListener { proxy.close() }
                            }
                        }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                        )
                    } catch (e: Exception) {
                        Log.e("PemindaiQr", "bind gagal", e)
                    }
                }, ContextCompat.getMainExecutor(context))
                previewView
            },
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spasi.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spasi.sm),
        ) {
            Text(
                "Arahkan kamera ke QR Setup dari dashboard",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onBatal) { Text("Batal") }
        }
    }
}
