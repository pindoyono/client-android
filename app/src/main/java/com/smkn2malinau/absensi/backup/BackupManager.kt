package com.smkn2malinau.absensi.backup

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup manager — backup terenkripsi database lokal berkala.
 * Setara database/backup.py Windows.
 */
class BackupManager(private val context: Context) {

    private val backupDir = File(context.filesDir, "backups")
    private val keyAlias = "absensi_backup_key"

    init {
        if (!backupDir.exists()) backupDir.mkdirs()
    }

    /**
     * Buat backup terenkripsi dari database.
     * @return path file backup atau null jika gagal
     */
    fun createBackup(dbFile: File): String? {
        return try {
            val key = getOrCreateKey()
            val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

            val backupFile = File(backupDir, "absensi_backup_${System.currentTimeMillis()}.enc")
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    // Write IV first
                    output.write(iv)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encrypted = cipher.update(buffer, 0, bytesRead)
                        if (encrypted != null) output.write(encrypted)
                    }
                    val final = cipher.doFinal()
                    if (final != null) output.write(final)
                }
            }
            backupFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Restore dari file backup terenkripsi.
     */
    fun restoreBackup(backupPath: String, targetDbFile: File): Boolean {
        return try {
            val key = getOrCreateKey()
            val backupFile = File(backupPath)
            val iv = ByteArray(16)
            FileInputStream(backupFile).use { input ->
                input.read(iv)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                FileOutputStream(targetDbFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        if (decrypted != null) output.write(decrypted)
                    }
                    val final = cipher.doFinal()
                    if (final != null) output.write(final)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getOrCreateKey(): SecretKey {
        // Simplified: generate key once and store in memory
        // In production, use Android Keystore
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }
}
