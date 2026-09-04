package com.smkn2malinau.absensi.face

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi/dekripsi embedding wajah — **format Fernet**, identik dengan server
 * (`app/services/crypto.py` → `cryptography.fernet.Fernet`) dan client Windows
 * (`app/face/crypto_embedding.py`).
 *
 * Fernet = AES-128-CBC + HMAC-SHA256, token base64url:
 *   `0x80 | timestamp(8, big-endian) | IV(16) | ciphertext | HMAC(32)`
 * Key = 32 byte base64url: `[0:16]` signing (HMAC), `[16:32]` enkripsi (AES-128).
 *
 * Float embedding di-pack **little-endian** (`struct.pack("<f>")` native x86/ARM).
 *
 * Key (`FACE_ENCRYPTION_KEY`) HARUS sama dengan server — didistribusikan manual
 * oleh admin (isi di Setup Device atau `local.properties`). Key salah → seluruh
 * embedding server tidak bisa didekripsi (matching selalu "tidak dikenali").
 */
object CryptoEmbedding {

    class KunciWajahSalah(pesan: String) : Exception(pesan)

    private const val VERSI: Byte = 0x80.toByte()

    /** Dekripsi token Fernet (byte base64url apa adanya dari server) → embedding. */
    fun decryptEmbedding(token: ByteArray, fernetKey: String): FloatArray {
        val (signingKey, encKey) = pisahKunci(fernetKey)
        val data = try {
            Base64.getUrlDecoder().decode(token)
        } catch (e: IllegalArgumentException) {
            throw KunciWajahSalah("Token embedding bukan base64url Fernet yang valid")
        }
        if (data.size < 1 + 8 + 16 + 32 || data[0] != VERSI) {
            throw KunciWajahSalah("Token Fernet tidak valid (versi/panjang salah)")
        }
        val batasHmac = data.size - 32
        val mac = hmac(signingKey, data.copyOfRange(0, batasHmac))
        if (!MessageDigest.isEqual(mac, data.copyOfRange(batasHmac, data.size))) {
            throw KunciWajahSalah(
                "HMAC Fernet tidak cocok — FACE_ENCRYPTION_KEY di device ini tidak sama dengan server"
            )
        }
        val iv = data.copyOfRange(9, 25)
        val ciphertext = data.copyOfRange(25, batasHmac)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        return bytesToFloatArray(cipher.doFinal(ciphertext))
    }

    /** Enkripsi embedding → token Fernet (byte base64url), untuk enroll lokal. */
    fun encryptEmbedding(embedding: FloatArray, fernetKey: String): ByteArray {
        val (signingKey, encKey) = pisahKunci(fernetKey)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(floatArrayToBytes(embedding))

        val timestamp = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putLong(System.currentTimeMillis() / 1000).array()
        val body = ByteArray(1 + 8 + 16 + ciphertext.size).also {
            it[0] = VERSI
            System.arraycopy(timestamp, 0, it, 1, 8)
            System.arraycopy(iv, 0, it, 9, 16)
            System.arraycopy(ciphertext, 0, it, 25, ciphertext.size)
        }
        val full = body + hmac(signingKey, body)
        return Base64.getUrlEncoder().encode(full)
    }

    private fun pisahKunci(fernetKey: String): Pair<ByteArray, ByteArray> {
        val bersih = fernetKey.trim()
        if (bersih.isEmpty()) {
            throw KunciWajahSalah("FACE_ENCRYPTION_KEY belum diisi — set di Setup Device / local.properties")
        }
        val raw = try {
            Base64.getUrlDecoder().decode(bersih)
        } catch (e: IllegalArgumentException) {
            throw KunciWajahSalah("FACE_ENCRYPTION_KEY bukan Fernet key base64url yang valid")
        }
        if (raw.size != 32) {
            throw KunciWajahSalah("FACE_ENCRYPTION_KEY harus 32 byte (Fernet key), dapat ${raw.size} byte")
        }
        return raw.copyOfRange(0, 16) to raw.copyOfRange(16, 32)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private fun floatArrayToBytes(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) buffer.putFloat(f)
        return buffer.array()
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "Panjang byte embedding tidak valid: ${bytes.size}" }
        val floats = FloatArray(bytes.size / 4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in floats.indices) floats[i] = buffer.float
        return floats
    }
}
