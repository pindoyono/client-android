package com.smkn2malinau.absensi.face

/**
 * Interface face engine — abstraksi untuk ONNX Runtime Mobile.
 * Mendukung deteksi wajah/liveness dan ekstraksi embedding.
 */
interface FaceEngine {
    suspend fun loadModels(livenessModelPath: String, embeddingModelPath: String)
    suspend fun extractEmbedding(bitmapBytes: ByteArray): FloatArray?
    suspend fun detectLiveness(bitmapBytes: ByteArray): LivenessResult?

    /**
     * Jalur ENROLLMENT — ekstraksi embedding TANPA cek liveness.
     * Setara `proses_frame(skip_liveness=True)` di client Windows: enrollment
     * dilakukan diawasi admin, anti-spoofing tidak relevan dan hanya bikin gagal.
     */
    suspend fun prosesFrameEnroll(frameBytes: ByteArray): HasilDeteksiWajah {
        val embedding = extractEmbedding(frameBytes)
        return HasilDeteksiWajah(
            wajahTerdeteksi = embedding != null,
            lolosLiveness = true,
            embedding = embedding,
            livenessScore = 1f,
            ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT,
            alasanGagal = if (embedding == null) "embedding_gagal" else null,
        )
    }

    /**
     * Jalur "satu frame → hasil" yang dipakai KioskViewModel (PRD bagian 4).
     * Menggabungkan deteksi liveness + ekstraksi embedding jadi satu hasil.
     */
    suspend fun prosesFrame(frameBytes: ByteArray): HasilDeteksiWajah {
        val liveness = detectLiveness(frameBytes)
            ?: return HasilDeteksiWajah(
                wajahTerdeteksi = false,
                lolosLiveness = false,
                embedding = null,
                livenessScore = 0f,
                ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT,
                alasanGagal = "wajah_tidak_terdeteksi"
            )

        if (!liveness.isReal) {
            return HasilDeteksiWajah(
                wajahTerdeteksi = true,
                lolosLiveness = false,
                embedding = null,
                livenessScore = liveness.score,
                ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT,
                alasanGagal = "gagal_liveness"
            )
        }

        val embedding = extractEmbedding(frameBytes)
        return HasilDeteksiWajah(
            wajahTerdeteksi = true,
            lolosLiveness = true,
            embedding = embedding,
            livenessScore = liveness.score,
            ambangLiveness = LivenessEvaluator.AMBANG_LIVENESS_DEFAULT,
            alasanGagal = if (embedding == null) "embedding_gagal" else null
        )
    }
}

data class LivenessResult(
    val score: Float,
    val isReal: Boolean,
    val confidence: Float
)

data class FaceDetectionResult(
    val boundingBox: FloatArray, // [x1, y1, x2, y2]
    val confidence: Float
)

/**
 * Hasil pemrosesan satu frame kamera — dikonsumsi KioskViewModel.
 */
data class HasilDeteksiWajah(
    val wajahTerdeteksi: Boolean,
    val lolosLiveness: Boolean,
    val embedding: FloatArray?,
    val livenessScore: Float,
    val ambangLiveness: Float,
    val alasanGagal: String?
)
