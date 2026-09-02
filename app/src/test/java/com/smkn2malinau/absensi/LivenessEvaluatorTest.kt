package com.smkn2malinau.absensi

import com.smkn2malinau.absensi.face.LivenessEvaluator
import org.junit.Assert.*
import org.junit.Test

/**
 * Test evaluasiLiveness — fungsi murni, tanpa kamera/ONNX.
 * PRD bagian 5 & 11.
 */
class LivenessEvaluatorTest {

    private val ambang = 0.752f

    // Skenario 1: skor tinggi → asli
    @Test
    fun `skor tinggi - dianggap asli`() {
        val output = floatArrayOf(0.95f)
        assertTrue(LivenessEvaluator.evaluasiLiveness(output, ambang))
    }

    // Skenario 2: skor rendah → spoofing
    @Test
    fun `skor rendah - dianggap spoofing`() {
        val output = floatArrayOf(0.10f)
        assertFalse(LivenessEvaluator.evaluasiLiveness(output, ambang))
    }

    // Skenario 3: persis di ambang batas → asli (>=)
    @Test
    fun `skor persis di ambang - dianggap asli`() {
        val output = floatArrayOf(0.752f)
        assertTrue(LivenessEvaluator.evaluasiLiveness(output, ambang))
    }

    // Skenario 4: sedikit di bawah ambang → spoofing
    @Test
    fun `skor sedikit di bawah ambang - spoofing`() {
        val output = floatArrayOf(0.751f)
        assertFalse(LivenessEvaluator.evaluasiLiveness(output, ambang))
    }

    // Skenario 5: output kosong → false (tidak aman)
    @Test
    fun `output kosong - dianggap spoofing`() {
        val output = floatArrayOf()
        assertFalse(LivenessEvaluator.evaluasiLiveness(output, ambang))
    }

    // Skenario 6: skor 0.5 (tengah) → spoofing
    @Test
    fun `skor tengah - spoofing`() {
        val output = floatArrayOf(0.5f)
        assertFalse(LivenessEvaluator.evaluasiLiveness(output, ambang))
    }

    // Test cosine similarity
    @Test
    fun `cosine similarity - vektor identik = 1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, LivenessEvaluator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `cosine similarity - vektor ortogonal = 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, LivenessEvaluator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `cocokkan wajah - embedding sama cocok`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f, 0f)
        assertTrue(LivenessEvaluator.cocokkanWajah(a, b, 0.3542f))
    }

    @Test
    fun `cocokkan wajah - embedding beda tidak cocok`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f, 0f)
        assertFalse(LivenessEvaluator.cocokkanWajah(a, b, 0.3542f))
    }

    // --- PRD bagian 3: verifikasi cocokkanWajah pakai definisi DISTANCE, bukan similarity mentah ---

    /**
     * Buat vektor 2D dengan cosine similarity persis = [sim] terhadap (1, 0).
     */
    private fun vektorDenganSimilarity(sim: Float): FloatArray {
        val y = kotlin.math.sqrt((1f - sim * sim).coerceAtLeast(0f))
        return floatArrayOf(sim, y)
    }

    @Test
    fun `cocokkan wajah - similarity 0_9 (distance 0_1) COCOK`() {
        val a = floatArrayOf(1f, 0f)
        val b = vektorDenganSimilarity(0.9f)
        assertEquals(0.1f, LivenessEvaluator.jarakWajah(a, b), 0.001f)
        assertTrue(LivenessEvaluator.cocokkanWajah(a, b, 0.3542f))
    }

    @Test
    fun `cocokkan wajah - similarity 0_6 (distance 0_4) TIDAK COCOK`() {
        val a = floatArrayOf(1f, 0f)
        val b = vektorDenganSimilarity(0.6f)
        assertEquals(0.4f, LivenessEvaluator.jarakWajah(a, b), 0.001f)
        // Kalau bug lama masih ada (similarity >= 0.3542), ini akan salah lolos jadi true.
        assertFalse(LivenessEvaluator.cocokkanWajah(a, b, 0.3542f))
    }

    @Test
    fun `cocokkan wajah - similarity persis di ambang distance TIDAK COCOK`() {
        val a = floatArrayOf(1f, 0f)
        val b = vektorDenganSimilarity(1f - 0.3542f) // distance == 0.3542, bukan < 0.3542
        assertFalse(LivenessEvaluator.cocokkanWajah(a, b, 0.3542f))
    }

    @Test
    fun `cocokkan wajah - ukuran beda atau kosong tidak cocok`() {
        assertFalse(LivenessEvaluator.cocokkanWajah(floatArrayOf(), floatArrayOf(), 0.3542f))
        assertFalse(LivenessEvaluator.cocokkanWajah(floatArrayOf(1f, 0f), floatArrayOf(1f), 0.3542f))
    }
}
