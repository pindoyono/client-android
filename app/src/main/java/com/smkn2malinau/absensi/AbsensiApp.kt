package com.smkn2malinau.absensi

import android.app.Application
import com.smkn2malinau.absensi.sync.SyncWorker

class AbsensiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Jadwalkan sync berkala (PRD bagian 9)
        SyncWorker.schedule(this)
    }
}
