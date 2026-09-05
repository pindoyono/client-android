package com.smkn2malinau.absensi.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Titik acuan geofencing device ini, apa adanya — di-cache dari GET /device/{id}/lokasi. */
data class KonfigLokasi(
    val lat: Double?,
    val lng: Double?,
    val radiusMeter: Int?,
)

/** Sama persis bentuknya dengan LokasiCekResponse dari server, supaya hasil offline
 * bisa dipakai lewat jalur simpanStatusLokasi yang sama seperti hasil online. */
data class HasilCekLokasi(
    val valid: Boolean,
    val alasan: String,
    val jarakMeter: Double? = null,
    val dikonfigurasi: Boolean = false,
)

/**
 * Duplikasi SENGAJA dari `app/services/geo.py` + logika keputusan di
 * `cek_lokasi_device()` (server) — dipakai SyncService saat `POST
 * /lokasi/cek` tidak terjangkau (device offline), supaya kiosk tetap bisa
 * memvalidasi jaraknya sendiri alih-alih cuma diam memakai status lama.
 * Kalau server & sisi ini pernah dites ulang, cek dua-duanya diubah bareng.
 */
object GeoOffline {

    private const val BUMI_RADIUS_METER = 6_371_000.0

    /** Jarak great-circle (formula Haversine) dalam meter. */
    fun jarakMeter(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lng2 - lng1)

        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return BUMI_RADIUS_METER * c
    }

    /**
     * Validasi lokal — dipanggil hanya saat online gagal. `lokasi` sudah
     * diambil SEBELUM percobaan online (tidak perlu fix GPS baru lagi).
     */
    fun validasi(lokasi: HasilLokasi, konfig: KonfigLokasi): HasilCekLokasi {
        val dikonfigurasi = konfig.lat != null && konfig.lng != null && konfig.radiusMeter != null
        return when {
            !dikonfigurasi -> HasilCekLokasi(
                valid = false,
                alasan = "lokasi belum diatur untuk device ini — hubungi admin [offline]",
            )
            !lokasi.tersedia -> HasilCekLokasi(
                valid = false,
                alasan = "lokasi tidak tersedia (izin ditolak / GPS mati) [offline]",
                dikonfigurasi = true,
            )
            lokasi.mock -> HasilCekLokasi(
                valid = false,
                alasan = "GPS palsu (mock location) terdeteksi [offline]",
                dikonfigurasi = true,
            )
            lokasi.lat == null || lokasi.lng == null -> HasilCekLokasi(
                valid = false,
                alasan = "koordinat tidak tersedia [offline]",
                dikonfigurasi = true,
            )
            else -> {
                val jarak = jarakMeter(konfig.lat!!, konfig.lng!!, lokasi.lat, lokasi.lng)
                if (jarak <= konfig.radiusMeter!!) {
                    HasilCekLokasi(valid = true, alasan = "dalam radius [offline]", jarakMeter = jarak, dikonfigurasi = true)
                } else {
                    HasilCekLokasi(
                        valid = false,
                        alasan = "di luar radius (${jarak.roundToInt()}m dari titik, batas ${konfig.radiusMeter}m) [offline]",
                        jarakMeter = jarak,
                        dikonfigurasi = true,
                    )
                }
            }
        }
    }
}
