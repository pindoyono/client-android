package com.smkn2malinau.absensi.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Komponen kamera menggunakan CameraX (PRD bagian 5).
 * Menggantikan cv2.VideoCapture dari Windows.
 *
 * Frame dikirim sebagai JPEG (self-describing: ukuran tersimpan di header)
 * supaya engine bisa decode Bitmap dengan dimensi benar — bukan raw Y plane
 * tanpa konteks.
 */
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    lensDepan: Boolean = true,
    onFrameAnalysis: (ByteArray) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor) { imageProxy ->
                            val rotasi = imageProxy.imageInfo.rotationDegrees
                            val jpeg = runCatching { imageProxy.toJpegBytes() }
                                .onFailure { e -> Log.w("CameraView", "Konversi frame gagal", e) }
                                .getOrNull()
                                ?.let { bytes -> if (rotasi != 0) rotateJpeg(bytes, rotasi) else bytes }
                            if (jpeg != null) onFrameAnalysis(jpeg)
                            imageProxy.close()
                        }
                    }

                val cameraSelector =
                    if (lensDepan) CameraSelector.DEFAULT_FRONT_CAMERA
                    else CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraView", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        }
    )
}

/** Konversi frame YUV_420_888 → JPEG (via NV21) agar bisa didekode ke Bitmap. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val nv21 = toNv21()
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, out)
    return out.toByteArray()
}

private const val JPEG_QUALITY = 85

/**
 * ImageAnalysis mengirim frame dalam orientasi sensor. Preview (`PreviewView`)
 * mengoreksi rotasi sendiri, tapi pipeline analisis TIDAK — akibatnya wajah
 * masuk ke ML Kit / model dalam keadaan miring dan tidak terdeteksi.
 * Putar JPEG ke tegak sesuai `rotationDegrees` sebelum diteruskan.
 */
private fun rotateJpeg(jpeg: ByteArray, degrees: Int): ByteArray {
    val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
    return try {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        val out = java.io.ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (rotated !== src) rotated.recycle()
        out.toByteArray()
    } catch (e: Exception) {
        Log.w("CameraView", "Rotasi frame gagal", e)
        jpeg
    } finally {
        if (!src.isRecycled) src.recycle()
    }
}

/** YUV_420_888 → NV21 (urutan byte yang dimengerti YuvImage). */
private fun ImageProxy.toNv21(): ByteArray {
    val w = width
    val h = height
    val ySize = w * h
    val out = ByteArray(ySize + 2 * (ySize / 2))

    // Plane Y
    val yPlane = planes[0]
    val yBuf = yPlane.buffer.duplicate()
    val yRowStride = yPlane.rowStride
    val yPixStride = yPlane.pixelStride
    if (yPixStride == 1 && yRowStride == w) {
        yBuf.get(out, 0, ySize)
    } else {
        var pos = 0
        for (row in 0 until h) {
            yBuf.position(row * yRowStride)
            for (col in 0 until w) {
                out[pos++] = yBuf.get(row * yRowStride + col * yPixStride)
            }
        }
    }

    // Plane U & V → interleave VU (NV21)
    val uPlane = planes[1]
    val vPlane = planes[2]
    val uBuf = uPlane.buffer.duplicate()
    val vBuf = vPlane.buffer.duplicate()
    val chromaW = w / 2
    val chromaH = h / 2
    val uRowStride = uPlane.rowStride
    val uPixStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixStride = vPlane.pixelStride
    var pos = ySize
    for (row in 0 until chromaH) {
        for (col in 0 until chromaW) {
            out[pos++] = vBuf.get(row * vRowStride + col * vPixStride)
            out[pos++] = uBuf.get(row * uRowStride + col * uPixStride)
        }
    }
    return out
}