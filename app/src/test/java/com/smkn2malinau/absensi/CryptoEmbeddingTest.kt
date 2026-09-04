package com.smkn2malinau.absensi

import com.smkn2malinau.absensi.face.CryptoEmbedding
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Verifikasi interop Fernet dengan server (`app/services/crypto.py`) & client Windows.
 * Token contoh di bawah dibuat dengan `cryptography.fernet` Python.
 */
class CryptoEmbeddingTest {

    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    @Test
    fun `dekripsi token Fernet buatan Python - cocok`() {
        // Fernet(key).encrypt(struct.pack("5f", 0.1, -0.2, 0.3, 1.0, -1.0))
        val token =
            "gAAAAABqmf38cM9h_3pP6qYcmL6KkBaPCVCzStosRcEKA3lSBHHTWoo0tSf45gbY4r8blyaT2Q4WA4iROs9vaeU_0LUfG6kPDBwYknXdggnTKGNZJkCJM-Y="
        val hasil = CryptoEmbedding.decryptEmbedding(token.toByteArray(Charsets.US_ASCII), key)
        assertArrayEquals(floatArrayOf(0.1f, -0.2f, 0.3f, 1.0f, -1.0f), hasil, 1e-5f)
    }

    @Test
    fun `round-trip encrypt lalu decrypt - konsisten`() {
        val emb = FloatArray(128) { (it - 64) / 64f }
        val token = CryptoEmbedding.encryptEmbedding(emb, key)
        assertArrayEquals(emb, CryptoEmbedding.decryptEmbedding(token, key), 1e-6f)
    }

    @Test
    fun `key salah - lempar KunciWajahSalah`() {
        val token = CryptoEmbedding.encryptEmbedding(floatArrayOf(1f, 2f, 3f), key)
        assertThrows(CryptoEmbedding.KunciWajahSalah::class.java) {
            CryptoEmbedding.decryptEmbedding(token, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        }
    }

    @Test
    fun `key kosong - lempar KunciWajahSalah`() {
        assertThrows(CryptoEmbedding.KunciWajahSalah::class.java) {
            CryptoEmbedding.decryptEmbedding("x".toByteArray(), "")
        }
    }

    @Test
    fun `IV acak - token beda tiap enkripsi tapi plaintext sama`() {
        val emb = floatArrayOf(0.1f, 0.2f, 0.3f)
        val a = CryptoEmbedding.encryptEmbedding(emb, key)
        val b = CryptoEmbedding.encryptEmbedding(emb, key)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(
            CryptoEmbedding.decryptEmbedding(a, key),
            CryptoEmbedding.decryptEmbedding(b, key),
            1e-6f,
        )
    }

    @Test
    fun `float embedding di-pack little-endian`() {
        // 1.0f little-endian = 00 00 80 3F. Kalau big-endian, byte[0] akan 0x3F.
        val token = CryptoEmbedding.encryptEmbedding(floatArrayOf(1.0f), key)
        val back = CryptoEmbedding.decryptEmbedding(token, key)
        assertEquals(1.0f, back[0], 0f)
    }
}
