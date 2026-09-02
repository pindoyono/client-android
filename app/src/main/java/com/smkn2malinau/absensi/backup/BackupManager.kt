package com.smkn2malinau.absensi.backup

import android.content.Context
import android.util.Log
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * BackupManager — backup terenkripsi database lokal berkala.
 * Setara database/backup.py Windows (PRD bagian 6).
 */
class BackupManager(private val context: Context) {

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /**
     * Menyalin file database ke direktori backup.
     * Karena menggunakan SQLCipher, file database sudah terenkripsi di disk.
     */
    fun performBackup(): Boolean {
        return try {
            val dbName = "absensi.db"
            val dbFile = context.getDatabasePath(dbName)
            
            if (!dbFile.exists()) {
                Log.w("BackupManager", "Database file does not exist")
                return false
            }

            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val timestamp = LocalDateTime.now().format(fmt)
            val backupFile = File(backupDir, "absensi_backup_$timestamp.db")

            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Hapus backup lama (sisakan 5 terakhir)
            cleanOldBackups(backupDir)

            Log.i("BackupManager", "Backup successful: ${backupFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Backup failed", e)
            false
        }
    }

    private fun cleanOldBackups(backupDir: File) {
        val files = backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > 5) {
            for (i in 5 until files.size) {
                files[i].delete()
            }
        }
    }
}
