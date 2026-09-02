package com.smkn2malinau.absensi

import android.app.Application
import android.util.Log
import com.smkn2malinau.absensi.sync.SyncWorker

class AbsensiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // PRD bagian 4.3 & 9 — jadwalkan sync periodik saat aplikasi start.
            SyncWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("AbsensiApp", "Gagal menjadwalkan SyncWorker", e)
        }
    }
}
