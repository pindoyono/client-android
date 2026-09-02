package com.smkn2malinau.absensi.face

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Dekripsi embedding — kompatibel dengan format enkripsi server.
 * Silakan sesuaikan algoritma & kunci dengan skema yang dipakai server.
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
            0x1F.toByte(), 0x2E.toByte(), 0x3D.toByte(), 0x4C.toByte(), 0x5B.toByte(), 0x6A.toByte(), 0x79.toByte(), 0x88.toByte(),
            0x97.toByte(), 0xA6.toByte(), 0xB5.toByte(), 0xC4.toByte(), 0xD3.toByte(), 0xE2.toByte(), 0xF1.toByte(), 0x00.toByte(),
            0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(), 0x55.toByte(), 0x66.toByte(), 0x77.toByte(), 0x88.toByte(),
            0x99.toByte(), 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x00.toByte()
        )
}
