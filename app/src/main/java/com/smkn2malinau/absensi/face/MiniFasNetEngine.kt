package com.smkn2malinau.absensi.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Implementasi FaceEngine menggunakan ONNX Runtime Mobile + ML Kit Face Detection.
 *
 * Alur (setara `client-windows/app/face/minifasnet_engine.py`):
 *  1. decode frame JPEG (dari CameraView)
 *  2. **deteksi wajah (ML Kit)** → crop ke kotak wajah + margin
 *     (menggantikan Haar cascade di Windows; frame kiosk sering berisi latar)
 *  3. resize ke ukuran input model
 *  4. normalisasi — MiniFasNet: `x/255`; ArcFace: `(x-127.5)/128` (NCHW float32)
 *  5. inference; liveness = softmax → kelas "asli" (indeks 2), clamp [0,1]
 */
class MiniFasNetEngine(context: Context) : FaceEngine {

    private var env: OrtEnvironment? = null
    private var livenessSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private val appContext = context.applicationContext
    @Volatile private var statusAkselerasi: String = "belum dimuat"

    override fun statusAkselerasi(): String = statusAkselerasi

    private val faceDetector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    override suspend fun loadModels(livenessModelPath: String, embeddingModelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                env = OrtEnvironment.getEnvironment()

                val livenessFile = copyAssetToCache(livenessModelPath)
                livenessSession = env?.createSession(livenessFile.absolutePath, buatSessionOptions())

                val embeddingFile = copyAssetToCache(embeddingModelPath)
                embeddingSession = env?.createSession(embeddingFile.absolutePath, buatSessionOptions())

                Log.d("MiniFasNetEngine", "Models loaded successfully")
            } catch (e: Exception) {
                Log.e("MiniFasNetEngine", "Failed to load models", e)
                throw e
            }
        }
    }

    /**
     * NNAPI execution provider bila didukung device (percepat inference dibanding CPU murni) —
     * gagal aktifkan (device/emulator tak dukung) tetap lanjut jalan di CPU, bukan fatal.
     */
    private fun buatSessionOptions(): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        try {
            options.addNnapi()
            statusAkselerasi = "NNAPI"
        } catch (e: Exception) {
            Log.w("MiniFasNetEngine", "NNAPI tidak tersedia, pakai CPU: ${e.message}")
            statusAkselerasi = "CPU (NNAPI tidak didukung)"
        }
        return options
    }

    // --- Jalur gabungan (deteksi wajah SEKALI, dipakai liveness + embedding) ---

    override suspend fun prosesFrame(frameBytes: ByteArray): HasilDeteksiWajah = withContext(Dispatchers.IO) {
        val ortEnv = env
        val livenessS = livenessSession
        val embeddingS = embeddingSession
        if (ortEnv == null || livenessS == null || embeddingS == null) return@withContext gagalDeteksi("engine_belum_siap")

        val wajah = decodeDanCropWajah(frameBytes) ?: return@withContext gagalDeteksi("wajah_tidak_terdeteksi")
        try {
            val skorLive = jalankanLiveness(ortEnv, livenessS, wajah)
            if (!LivenessEvaluator.evaluasiLiveness(floatArrayOf(skorLive))) {
                return@withContext HasilDeteksiWajah(
                    wajahTerdeteksi = true, lolosLiveness = false, embedding = null,
                    livenessScore = skorLive, ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT,
                    alasanGagal = "gagal_liveness",
                )
            }
            val embedding = jalankanEmbedding(ortEnv, embeddingS, wajah)
            HasilDeteksiWajah(
                wajahTerdeteksi = true, lolosLiveness = true, embedding = embedding,
                livenessScore = skorLive, ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT,
                alasanGagal = if (embedding == null) "embedding_gagal" else null,
            )
        } catch (e: Exception) {
            Log.e("MiniFasNetEngine", "prosesFrame gagal", e)
            gagalDeteksi("error_inference")
        } finally {
            if (!wajah.isRecycled) wajah.recycle()
        }
    }

    override suspend fun prosesFrameEnroll(frameBytes: ByteArray): HasilDeteksiWajah = withContext(Dispatchers.IO) {
        val ortEnv = env
        val embeddingS = embeddingSession
        if (ortEnv == null || embeddingS == null) return@withContext gagalDeteksi("engine_belum_siap")

        val wajah = decodeDanCropWajah(frameBytes) ?: return@withContext gagalDeteksi("wajah_tidak_terdeteksi")
        try {
            val embedding = jalankanEmbedding(ortEnv, embeddingS, wajah)
            HasilDeteksiWajah(
                wajahTerdeteksi = embedding != null, lolosLiveness = true, embedding = embedding,
                livenessScore = 1f, ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT,
                alasanGagal = if (embedding == null) "embedding_gagal" else null,
            )
        } catch (e: Exception) {
            Log.e("MiniFasNetEngine", "prosesFrameEnroll gagal", e)
            gagalDeteksi("error_inference")
        } finally {
            if (!wajah.isRecycled) wajah.recycle()
        }
    }

    // --- Jalur tunggal (interface — dipakai test / pemanggil lain) ---

    override suspend fun extractEmbedding(bitmapBytes: ByteArray): FloatArray? = withContext(Dispatchers.IO) {
        val ortEnv = env ?: return@withContext null
        val session = embeddingSession ?: return@withContext null
        val wajah = decodeDanCropWajah(bitmapBytes) ?: return@withContext null
        try {
            jalankanEmbedding(ortEnv, session, wajah)
        } catch (e: Exception) {
            Log.e("MiniFasNetEngine", "Embedding extraction failed", e)
            null
        } finally {
            if (!wajah.isRecycled) wajah.recycle()
        }
    }

    override suspend fun detectLiveness(bitmapBytes: ByteArray): LivenessResult? = withContext(Dispatchers.IO) {
        val ortEnv = env ?: return@withContext null
        val session = livenessSession ?: return@withContext null
        val wajah = decodeDanCropWajah(bitmapBytes) ?: return@withContext null
        try {
            val score = jalankanLiveness(ortEnv, session, wajah)
            LivenessResult(score, LivenessEvaluator.evaluasiLiveness(floatArrayOf(score)), score)
        } catch (e: Exception) {
            Log.e("MiniFasNetEngine", "Liveness detection failed", e)
            null
        } finally {
            if (!wajah.isRecycled) wajah.recycle()
        }
    }

    // --- Inference ---

    private fun jalankanLiveness(ortEnv: OrtEnvironment, session: OrtSession, wajah: Bitmap): Float {
        val input = preprocessBitmap(wajah, LIVENESS_SIZE, Normalisasi.SKALA_255)
        val shape = longArrayOf(1, 3, LIVENESS_SIZE.toLong(), LIVENESS_SIZE.toLong())
        OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape).use { tensor ->
            val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
            val vektor = (result.get(0).value as? Array<FloatArray>)?.getOrNull(0)
            return if (vektor == null || vektor.isEmpty()) 0f else probKelasLive(vektor)
        }
    }

    private fun jalankanEmbedding(ortEnv: OrtEnvironment, session: OrtSession, wajah: Bitmap): FloatArray? {
        val input = preprocessBitmap(wajah, EMBEDDING_SIZE, Normalisasi.ARCFACE)
        val shape = longArrayOf(1, 3, EMBEDDING_SIZE.toLong(), EMBEDDING_SIZE.toLong())
        OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape).use { tensor ->
            val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
            return (result.get(0).value as? Array<FloatArray>)?.getOrNull(0)?.also { l2Normalize(it) }
        }
    }

    // --- Deteksi & crop wajah ---

    /** Decode JPEG → deteksi wajah terbesar (ML Kit) → crop kotak + margin. null = tak ada wajah. */
    private fun decodeDanCropWajah(jpegBytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        return try {
            val box = deteksiKotakWajah(bitmap)
            if (box == null) {
                bitmap.recycle()
                null
            } else {
                val crop = cropDenganMargin(bitmap, box, MARGIN_WAJAH)
                if (crop !== bitmap) bitmap.recycle()
                crop
            }
        } catch (e: Exception) {
            Log.w("MiniFasNetEngine", "Deteksi wajah gagal", e)
            if (!bitmap.isRecycled) bitmap.recycle()
            null
        }
    }

    private fun deteksiKotakWajah(bitmap: Bitmap): Rect? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = Tasks.await(faceDetector.process(image))
        return faces.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }?.boundingBox
    }

    private fun cropDenganMargin(src: Bitmap, box: Rect, margin: Float): Bitmap {
        val mx = (box.width() * margin).toInt()
        val my = (box.height() * margin).toInt()
        val left = (box.left - mx).coerceIn(0, src.width - 1)
        val top = (box.top - my).coerceIn(0, src.height - 1)
        val right = (box.right + mx).coerceIn(left + 1, src.width)
        val bottom = (box.bottom + my).coerceIn(top + 1, src.height)
        return Bitmap.createBitmap(src, left, top, right - left, bottom - top)
    }

    // --- Preprocessing tensor ---

    private enum class Normalisasi { SKALA_255, ARCFACE }

    private fun preprocessBitmap(face: Bitmap, size: Int, norm: Normalisasi): FloatArray {
        val scaled = Bitmap.createScaledBitmap(face, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        if (scaled !== face) scaled.recycle()

        // NCHW. MiniFasNet: x/255 → [0,1]. ArcFace: (x-127.5)/128 → ~[-1,1].
        val out = FloatArray(3 * size * size)
        val plane = size * size
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF).toFloat()
            val g = (p shr 8 and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            when (norm) {
                Normalisasi.SKALA_255 -> {
                    out[i] = r / 255f
                    out[plane + i] = g / 255f
                    out[2 * plane + i] = b / 255f
                }
                Normalisasi.ARCFACE -> {
                    out[i] = (r - 127.5f) / 128f
                    out[plane + i] = (g - 127.5f) / 128f
                    out[2 * plane + i] = (b - 127.5f) / 128f
                }
            }
        }
        return out
    }

    /**
     * Softmax lalu ambil probabilitas kelas "wajah asli".
     * MiniFasNet (Silent-Face) keluar sbg logit multi-kelas — client Windows pakai
     * `INDEKS_KELAS_LIVE = 2` (model 3 kelas). Untuk model 2 kelas ambil indeks 1.
     */
    private fun probKelasLive(logits: FloatArray): Float {
        val idx = when {
            logits.size > INDEKS_KELAS_LIVE -> INDEKS_KELAS_LIVE
            logits.size == 2 -> 1
            else -> return logits[0].coerceIn(0f, 1f)
        }
        val maks = logits.max()
        var jml = 0.0
        for (l in logits) jml += exp((l - maks).toDouble())
        if (jml == 0.0) return 0f
        val p = exp((logits[idx] - maks).toDouble()) / jml
        return p.toFloat().coerceIn(0f, 1f)
    }

    /** Normalisasi L2 — ArcFace distance memakai cosine pada vektor satuan. */
    private fun l2Normalize(v: FloatArray) {
        var sum = 0.0
        for (f in v) sum += f.toDouble() * f.toDouble()
        val norm = sqrt(sum).toFloat()
        if (norm > 0f) for (i in v.indices) v[i] /= norm
    }

    private fun gagalDeteksi(alasan: String) = HasilDeteksiWajah(
        wajahTerdeteksi = false, lolosLiveness = false, embedding = null,
        livenessScore = 0f, ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT, alasanGagal = alasan,
    )

    private fun copyAssetToCache(assetPath: String): File {
        val cacheFile = File(appContext.cacheDir, assetPath.substringAfterLast('/'))
        if (cacheFile.exists()) return cacheFile
        appContext.assets.open(assetPath).use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        return cacheFile
    }

    companion object {
        private const val EMBEDDING_SIZE = 112  // ArcFace input
        private const val LIVENESS_SIZE = 80    // MiniFasNet input
        /** Indeks kelas "wajah asli" pada output MiniFasNet — verifikasi webcam = 2 (client Windows). */
        private const val INDEKS_KELAS_LIVE = 2
        /**
         * Margin di sekitar kotak wajah ML Kit. Client Windows memakai kotak Haar
         * apa adanya (margin 0) — samakan supaya embedding ArcFace sebanding.
         */
        private const val MARGIN_WAJAH = 0.0f
    }
}
