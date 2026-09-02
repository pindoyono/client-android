package com.smkn2malinau.absensi.face

/**
 * Interface face engine — abstraksi untuk ONNX Runtime Mobile.
 * Mendukung deteksi wajah/liveness dan ekstraksi embedding.
 */
interface FaceEngine {
    suspend fun loadModels(livenessModelPath: String, embeddingModelPath: String)
    suspend fun extractEmbedding(bitmapBytes: ByteArray): FloatArray?
    suspend fun detectLiveness(bitmapBytes: ByteArray): LivenessResult?
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
