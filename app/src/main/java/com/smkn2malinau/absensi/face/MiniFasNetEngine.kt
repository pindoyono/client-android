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
import java.nio.FloatBuffer

/**
 * Implementasi FaceEngine menggunakan ONNX Runtime Mobile.
 * Mengelola dua sesi: satu untuk liveness (MiniFasNet) dan satu untuk embedding (ArcFace).
 */
class MiniFasNetEngine(context: Context) : FaceEngine {

    private var env: OrtEnvironment? = null
    private var livenessSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private val appContext = context.applicationContext

    override suspend fun loadModels(livenessModelPath: String, embeddingModelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions()
                
                val livenessFile = copyAssetToCache(livenessModelPath)
                livenessSession = env?.createSession(livenessFile.absolutePath, options)
                
                val embeddingFile = copyAssetToCache(embeddingModelPath)
                embeddingSession = env?.createSession(embeddingFile.absolutePath, options)
                
                Log.d("MiniFasNetEngine", "Models loaded successfully")
            } catch (e: Exception) {
                Log.e("MiniFasNetEngine", "Failed to load models", e)
                throw e
            }
        }
    }

    override suspend fun extractEmbedding(bitmapBytes: ByteArray): FloatArray? {
        return withContext(Dispatchers.IO) {
            try {
                val ortEnv = env ?: return@withContext null
                val session = embeddingSession ?: return@withContext null

                // Preprocess placeholder (ArcFace usually takes 112x112)
                val input = FloatArray(112 * 112 * 3)
                val shape = longArrayOf(1, 3, 112, 112)
                val floatBuffer = FloatBuffer.wrap(input)

                OnnxTensor.createTensor(ortEnv, floatBuffer, shape).use { tensor ->
                    val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
                    val output = result.get(0).value as? Array<FloatArray>
                    output?.get(0)
                }
            } catch (e: Exception) {
                Log.e("MiniFasNetEngine", "Embedding extraction failed", e)
                null
            }
        }
    }

    override suspend fun detectLiveness(bitmapBytes: ByteArray): LivenessResult? {
        return withContext(Dispatchers.IO) {
            try {
                val ortEnv = env ?: return@withContext null
                val session = livenessSession ?: return@withContext null

                // Preprocess placeholder (MiniFasNet usually takes 80x80)
                val input = FloatArray(80 * 80 * 3)
                val shape = longArrayOf(1, 3, 80, 80)
                val floatBuffer = FloatBuffer.wrap(input)

                OnnxTensor.createTensor(ortEnv, floatBuffer, shape).use { tensor ->
                    val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
                    val output = result.get(0).value as? Array<FloatArray>
                    val score = output?.get(0)?.get(0) ?: 0f
                    
                    LivenessResult(
                        score = score,
                        isReal = LivenessEvaluator.evaluasiLiveness(floatArrayOf(score)),
                        confidence = score
                    )
                }
            } catch (e: Exception) {
                Log.e("MiniFasNetEngine", "Liveness detection failed", e)
                null
            }
        }
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
