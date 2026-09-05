package com.smkn2malinau.absensi.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.remote.ApiClientProvider
import com.smkn2malinau.absensi.location.FusedLocationChecker
import com.smkn2malinau.absensi.security.CredentialManager
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val credentialManager = CredentialManager(applicationContext)
            val deviceId = credentialManager.getDeviceId()
            val apiKey = credentialManager.getApiKey()

            if (deviceId == null || apiKey == null) {
                return Result.failure()
            }

            val passphrase = credentialManager.getDbPassphrase()
            val db = AbsensiDatabase.getDatabase(applicationContext, passphrase)
            val repo = SyncRepositoryImpl(db)
            val api = ApiClientProvider.create(deviceId, apiKey, credentialManager.getServerBaseUrl())

            val syncService = SyncService(
                repo, api, deviceId,
                locationChecker = FusedLocationChecker(applicationContext),
                simpanStatusLokasi = { valid, alasan, jarak, dikonfigurasi ->
                    credentialManager.setStatusLokasi(valid, alasan, jarak, dikonfigurasi)
                },
            )
            val result = syncService.runSyncCycle()

            when (result) {
                is SyncResult.Success -> Result.success()
                is SyncResult.Failure -> Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "sync_worker"
        const val WORK_SEKALI = "sync_worker_sekali"

        /**
         * Sync satu kali. Nama unik: pemicu berulang tidak menumpuk.
         * @param paksa true (tombol manual) → REPLACE, mulai ulang walau ada yg jalan.
         *              false (pemicu otomatis: kiosk dibuka / tiap absen) → KEEP,
         *              jangan ganggu siklus yang sedang berjalan.
         */
        fun enqueueSekali(context: Context, paksa: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            val policy = if (paksa) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_SEKALI, policy, request)
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
