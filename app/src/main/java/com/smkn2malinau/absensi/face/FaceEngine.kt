package com.smkn2malinau.absensi.face

import java.nio.ByteBuffer

/**
 * Interface face engine — abstraksi untuk ONNX Runtime Mobile.
 */
interface FaceEngine {
    suspend fun loadModel(modelPath: String)
    suspend fun extractEmbedding(bitmapBytes: ByteArray): FloatArray?
    suspend fun detectFace(bitmapBytes: ByteArray): FaceDetectionResult?
}

data class FaceDetectionResult(
    val boundingBox: FloatArray, // [x1, y1, x2, y2]
    val confidence: Float
)

data class EmbeddingResult(
    val embedding: FloatArray,
    val faceBox: FloatArray
)