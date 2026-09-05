package com.smkn2malinau.absensi.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Hasil satu percobaan ambil lokasi — dikirim server via `POST /device/{id}/lokasi/cek`. */
data class HasilLokasi(
    val tersedia: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    val akurasiMeter: Float? = null,
    /**
     * `LocationCompat.isMock()` — true kalau OS Android menandai lokasi ini
     * berasal dari mock-location provider (Developer Options > Select mock
     * location app). Ini mendeteksi pemakaian fake-GPS "biasa" dengan andal,
     * TAPI bukan jaminan mutlak: di device root dengan modul Xposed/Magisk
     * khusus, flag ini bisa dipalsukan juga. Anggap sebagai pertahanan
     * lapis-pertama, bukan bukti kriptografis.
     */
    val mock: Boolean = false,
)

interface LocationChecker {
    suspend fun ambilLokasiSaatIni(): HasilLokasi

    companion object {
        /** Dipakai sebagai default param SyncService — server menganggap ini "tidak tersedia". */
        val TidakTersedia = object : LocationChecker {
            override suspend fun ambilLokasiSaatIni() = HasilLokasi(tersedia = false)
        }
    }
}

/**
 * Implementasi nyata via Fused Location Provider. Device kiosk TIDAK
 * berpindah antar-scan, jadi ini dipanggil berkala oleh SyncService
 * (bukan tiap frame kamera) — satu fix GPS bisa makan beberapa detik,
 * terlalu lambat untuk jalur pengenalan wajah.
 */
class FusedLocationChecker(context: Context) : LocationChecker {
    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    override suspend fun ambilLokasiSaatIni(): HasilLokasi {
        if (!izinAda()) return HasilLokasi(tersedia = false)

        val lokasi = try {
            withTimeoutOrNull(TIMEOUT_MS) { ambilFixSekarang() }
        } catch (e: SecurityException) {
            null
        }

        return if (lokasi == null) {
            HasilLokasi(tersedia = false)
        } else {
            HasilLokasi(
                tersedia = true,
                lat = lokasi.latitude,
                lng = lokasi.longitude,
                akurasiMeter = if (lokasi.hasAccuracy()) lokasi.accuracy else null,
                mock = LocationCompat.isMock(lokasi),
            )
        }
    }

    private fun izinAda(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private suspend fun ambilFixSekarang(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                .addOnFailureListener { cont.resumeWith(Result.success(null)) }
        } catch (e: SecurityException) {
            cont.resumeWith(Result.success(null))
        }
    }

    companion object {
        private const val TIMEOUT_MS = 20_000L
    }
}
