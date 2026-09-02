package com.smkn2malinau.absensi.face

/**
 * Fungsi murni evaluasi liveness — dipisahkan dari kode kamera/ONNX.
 * Bisa diuji dengan angka tiruan, tanpa kamera.
 * PRD bagian 5 & 11 — pelajaran dari bug Windows: jangan campur logika dengan akses kamera.
 */
object LivenessEvaluator {

    /** Ambang skor liveness (kalibrasi Windows, perlu dikalibrasi ulang untuk Android). */
    const val AMBANG_LIVENESS_DEFAULT = 0.752f

    /** Ambang *distance* face-matching (kalibrasi Windows, perlu dikalibrasi ulang). */
    const val AMBANG_JARAK_DEFAULT = 0.3542f

    /**
     * Evaluasi apakah wajah asli (real) atau spoof berdasarkan output liveness model.
     *
     * @param output output float array dari model liveness (skor 0.0 - 1.0)
     * @param ambang nilai ambang batas (default 0.752 — nilai kalibrasi Windows, perlu dikalibrasi ulang untuk Android)
     * @return true jika asli (skor >= ambang)
     */
    fun evaluasiLiveness(output: FloatArray, ambang: Float = AMBANG_LIVENESS_DEFAULT): Boolean {
        if (output.isEmpty()) return false
        // Ambil skor rata-rata atau skor pertama (sesuai format output model)
        val skor = output.first()
        return skor >= ambang
    }

    /**
     * Hitung confidence score dari output.
     */
    fun hitungConfidence(output: FloatArray): Float {
        if (output.isEmpty()) return 0f
        return output.first()
    }

    /**
     * Bandingkan dua embedding (face matching).
     *
     * PENTING — definisi ambang HARUS sama dengan Windows (`minifasnet_engine.py`):
     *   distance = 1.0 - cosine_similarity     (0 = identik, 2 = berlawanan)
     *   cocok jika distance < ambangJarak
     * Windows mengkalibrasi `0.3542` sebagai ambang *distance*, bukan similarity mentah.
     * (Kalibrasi ulang untuk kamera Android tetap diperlukan — itu urusan terpisah.)
     *
     * @param ambangJarak ambang *distance* (default 0.3542, kalibrasi Windows).
     */
    fun cocokkanWajah(embedding1: FloatArray, embedding2: FloatArray, ambangJarak: Float = AMBANG_JARAK_DEFAULT): Boolean {
        if (embedding1.isEmpty() || embedding1.size != embedding2.size) return false
        return jarakWajah(embedding1, embedding2) < ambangJarak
    }

    /**
     * Jarak antar wajah = 1 - cosine similarity. Samakan definisi dengan Windows.
     */
    fun jarakWajah(embedding1: FloatArray, embedding2: FloatArray): Float =
        1f - cosineSimilarity(embedding1, embedding2)

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        if (denom == 0f) return 0f
        return dot / denom
    }
}