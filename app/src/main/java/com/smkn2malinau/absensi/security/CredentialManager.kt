package com.smkn2malinau.absensi.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credential manager — simpan Device API Key & device ID di Android Keystore.
 * Setara Windows Credential Manager (PRD bagian 5).
 */
class CredentialManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("absensi_secure_prefs", Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val KEY_ALIAS = "absensi_device_key"
    private val PREF_DEVICE_ID = "device_id"
    private val PREF_API_KEY = "api_key_enc"
    private val PREF_DB_PASSPHRASE = "db_passphrase_enc"
    private val PREF_ON_SITE_TESTING = "ON_SITE_TESTING_SELESAI"

    /**
     * Simpan Device API Key (terenkripsi di Keystore).
     */
    fun saveApiKey(apiKey: String) {
        val encrypted = encrypt(apiKey)
        prefs.edit().putString(PREF_API_KEY, encrypted).apply()
    }

    fun getApiKey(): String? {
        val encrypted = prefs.getString(PREF_API_KEY, null) ?: return null
        return try {
            decrypt(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Simpan device ID.
     */
    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(PREF_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): String? = prefs.getString(PREF_DEVICE_ID, null)

    /**
     * Simpan passphrase database (SQLCipher).
     */
    fun saveDbPassphrase(passphrase: String) {
        val encrypted = encrypt(passphrase)
        prefs.edit().putString(PREF_DB_PASSPHRASE, encrypted).apply()
    }

    fun getDbPassphrase(): ByteArray {
        val encrypted = prefs.getString(PREF_DB_PASSPHRASE, null)
        if (encrypted != null) {
            return try {
                decrypt(encrypted).toByteArray()
            } catch (e: Exception) {
                generateDefaultPassphrase()
            }
        }
        return generateDefaultPassphrase()
    }

    /**
     * Safety gate — PRD bagian 10.
     */
    fun isOnSiteTestingSelesai(): Boolean =
        prefs.getBoolean(PREF_ON_SITE_TESTING, false)

    fun setOnSiteTestingSelesai(value: Boolean) {
        prefs.edit().putBoolean(PREF_ON_SITE_TESTING, value).apply()
    }

    private fun generateDefaultPassphrase(): ByteArray {
        val pass = "absensi_smkn2_malinau_2024_secure_db"
        saveDbPassphrase(pass)
        return pass.toByteArray()
    }

    private fun getOrCreateKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getKey(KEY_ALIAS, null) as SecretKey)
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedText: String): String {
        val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
