package com.smkn2malinau.absensi.face

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Implementasi FaceEngine menggunakan ONNX Runtime Mobile.
 * Model: minifasnet.onnx + arcface.onnx di app/src/main/assets/models/
 */
class MiniFasNetEngine(context: Context) : FaceEngine {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val appContext = context.applicationContext

    override suspend fun loadModel(modelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                val modelFile = copyAssetToCache(modelPath)
                env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions()
                session = env?.createSession(modelFile.absolutePath, options)
                Log.d("MiniFasNetEngine", "Model loaded: $modelPath")
            } catch (e: Exception) {
                Log.e("MiniFasNetEngine", "Failed to load model", e)
                throw e
            }
        }
    }

    override suspend fun extractEmbedding(bitmapBytes: ByteArray): FloatArray? {
        return withContext(Dispatchers.IO) {
            try {
                val ortEnv = env ?: return@withContext null
                val ortSession = session ?: return@withContext null

                // Preprocess bitmap to tensor (dummy 512-dim embedding placeholder)
                // Real implementation: face detection → align → embed
                val input = FloatArray(512)
                val shape = longArrayOf(1, 512)

                OnnxTensor.createTensor(ortEnv, input, shape).use { tensor ->
                    val result = ortSession.run(mapOf(ortSession.inputNames.iterator().next() to tensor))
                    result.get(0) as? FloatArray
                }
            } catch (e: Exception) {
                Log.e("MiniFasNetEngine", "Embedding extraction failed", e)
                null
            }
        }
    }

    override suspend fun detectFace(bitmapBytes: ByteArray): FaceDetectionResult? {
        // Placeholder — real detection uses minifasnet.onnx
        return FaceDetectionResult(floatArrayOf(0f, 0f, 1f, 1f), 0.9f)
    }

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
}
