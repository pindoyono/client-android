package com.smkn2malinau.absensi.face

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Dekripsi embedding — kompatibel dengan format enkripsi server.
 * Silakan sesuaikan algoritma & kunci dengan skema yang dipakai server
 * (cek docs/API_CONTRACT.md di repo server).
 */
object CryptoEmbedding {

    private const val ALGORITHM = "AES"
    private const val CIPHER = "AES/ECB/PKCS5Padding"

    /**
     * Dekripsi embedding dari byte array terenkripsi.
     * @param key kunci AES (32 byte untuk AES-256)
     */
    fun decryptEmbedding(encrypted: ByteArray, key: ByteArray = DEFAULT_KEY): FloatArray {
        val secretKey = SecretKeySpec(key, ALGORITHM)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val plainText = cipher.doFinal(encrypted)
        return bytesToFloatArray(plainText)
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "Invalid embedding byte length" }
        val floats = FloatArray(bytes.size / 4)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }

    private val DEFAULT_KEY: ByteArray
        get() = byteArrayOf(
            0x1F, 0x2E, 0x3D, 0x4C, 0x5B, 0x6A, 0x79, 0x88,
            0x97, 0xA6, 0xB5, 0xC4, 0xD3, 0xE2, 0xF1, 0x00,
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
            0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x00
        )
}