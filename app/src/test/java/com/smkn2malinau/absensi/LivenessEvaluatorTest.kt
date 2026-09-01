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
}
