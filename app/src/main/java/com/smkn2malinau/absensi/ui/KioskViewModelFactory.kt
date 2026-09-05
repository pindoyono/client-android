package com.smkn2malinau.absensi.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smkn2malinau.absensi.business.AttendanceLogic
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.face.MiniFasNetEngine
import com.smkn2malinau.absensi.repository.AbsensiRepositoryImpl
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.util.NetworkMonitor

/**
 * Wiring manual (tanpa DI framework) untuk KioskViewModel.
 */
class KioskViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext

    companion object {
        const val LIVENESS_MODEL = "models/minifasnet.onnx"
        const val EMBEDDING_MODEL = "models/arcface.onnx"
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(KioskViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        val credentialManager = CredentialManager(appContext)
        val deviceId = credentialManager.getDeviceId() ?: "unknown-device"
        val db = AbsensiDatabase.getDatabase(appContext, credentialManager.getDbPassphrase())
        val faceEngine = MiniFasNetEngine(appContext)

        return KioskViewModel(
            faceEngine = faceEngine,
            attendanceLogic = AttendanceLogic(),
            repo = AbsensiRepositoryImpl(db, deviceId, credentialManager.getFaceKey()),
            onlineFlow = NetworkMonitor(appContext).onlineFlow,
            onSiteTestingSelesai = { credentialManager.isOnSiteTestingSelesai() },
            ambangJarak = credentialManager.getAmbangJarak(),
            picuSinkron = { com.smkn2malinau.absensi.sync.SyncWorker.enqueueSekali(appContext) },
            muatModel = { faceEngine.loadModels(LIVENESS_MODEL, EMBEDDING_MODEL) },
            lokasiValidProvider = { credentialManager.lokasiValid() },
            lokasiAlasanProvider = { credentialManager.lokasiAlasan() },
            lokasiJarakProvider = { credentialManager.lokasiJarakMeter() },
        ) as T
    }
}
