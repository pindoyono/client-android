package com.smkn2malinau.absensi

import android.app.Application
import android.util.Log
import com.smkn2malinau.absensi.auth.seedAdminDefault
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AbsensiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // PRD bagian 4.3 & 9 — jadwalkan sync periodik saat aplikasi start.
            SyncWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("AbsensiApp", "Gagal menjadwalkan SyncWorker", e)
        }
        seedAdminDefault()
    }

    /** Akun admin offline default dari BuildConfig (local.properties, tidak di-commit). */
    private fun seedAdminDefault() {
        val user = BuildConfig.DEFAULT_ADMIN_USER
        val pass = BuildConfig.DEFAULT_ADMIN_PASS
        if (user.isBlank() || pass.isBlank()) return
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val cm = CredentialManager(this@AbsensiApp)
                val db = AbsensiDatabase.getDatabase(applicationContext, cm.getDbPassphrase())
                db.akunDao().seedAdminDefault(user, pass)
            } catch (e: Exception) {
                Log.w("AbsensiApp", "Seed admin default gagal", e)
            }
        }
    }
}
