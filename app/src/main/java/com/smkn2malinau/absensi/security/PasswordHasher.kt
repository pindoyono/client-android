package com.smkn2malinau.absensi.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash password akun lokal — PBKDF2-HMAC-SHA256. Dipakai untuk login offline
 * (server tidak menyimpan password; akun & hash hanya ada di device, terenkripsi
 * SQLCipher). Verifikasi konstan-waktu.
 */
object PasswordHasher {

    private const val ITERASI = 120_000
    private const val PANJANG_KEY_BIT = 256
    private const val PANJANG_SALT = 16

    data class Hash(val hashB64: String, val saltB64: String)

    fun hash(password: CharArray): Hash {
        val salt = ByteArray(PANJANG_SALT).also { SecureRandom().nextBytes(it) }
        return Hash(Base64.getEncoder().encodeToString(pbkdf2(password, salt)), Base64.getEncoder().encodeToString(salt))
    }

    fun hash(password: String): Hash = hash(password.toCharArray())

    fun verifikasi(password: String, hashB64: String?, saltB64: String?): Boolean {
        if (hashB64.isNullOrBlank() || saltB64.isNullOrBlank()) return false
        return try {
            val salt = Base64.getDecoder().decode(saltB64)
            val dihitung = pbkdf2(password.toCharArray(), salt)
            MessageDigest.isEqual(dihitung, Base64.getDecoder().decode(hashB64))
        } catch (e: Exception) {
            false
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERASI, PANJANG_KEY_BIT)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
