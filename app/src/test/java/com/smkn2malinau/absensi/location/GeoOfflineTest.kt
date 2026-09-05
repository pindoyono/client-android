package com.smkn2malinau.absensi.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Duplikasi sengaja dari test server `test_geofencing_device.py` untuk
 * `jarak_meter()` + `cek_lokasi_device()` — kedua sisi harus tetap sepakat.
 */
class GeoOfflineTest {

    private val titikLat = -3.4295
    private val titikLng = 116.4396

    @Test
    fun `jarak ke titik yang sama nol`() {
        assertEquals(0.0, GeoOffline.jarakMeter(titikLat, titikLng, titikLat, titikLng), 0.01)
    }

    @Test
    fun `jarak 0,01 derajat lintang sekitar 1,1km`() {
        val jarak = GeoOffline.jarakMeter(titikLat, titikLng, titikLat + 0.01, titikLng)
        assertTrue("jarak=$jarak", jarak in 1000.0..1200.0)
    }

    @Test
    fun `validasi - belum dikonfigurasi - selalu ditolak`() {
        val hasil = GeoOffline.validasi(
            HasilLokasi(tersedia = true, lat = titikLat, lng = titikLng),
            KonfigLokasi(null, null, null),
        )
        assertFalse(hasil.valid)
        assertFalse(hasil.dikonfigurasi)
        assertTrue(hasil.alasan.contains("belum diatur"))
    }

    @Test
    fun `validasi - lokasi tidak tersedia - ditolak`() {
        val hasil = GeoOffline.validasi(
            HasilLokasi(tersedia = false),
            KonfigLokasi(titikLat, titikLng, 100),
        )
        assertFalse(hasil.valid)
        assertTrue(hasil.dikonfigurasi) // sudah dikonfigurasi, cuma lokasi HP-nya yang tak tersedia
        assertTrue(hasil.alasan.contains("tidak tersedia"))
    }

    @Test
    fun `validasi - mock location - ditolak`() {
        val hasil = GeoOffline.validasi(
            HasilLokasi(tersedia = true, lat = titikLat, lng = titikLng, mock = true),
            KonfigLokasi(titikLat, titikLng, 100),
        )
        assertFalse(hasil.valid)
        assertTrue(hasil.alasan.contains("palsu"))
    }

    @Test
    fun `validasi - dalam radius - diterima dengan jarak`() {
        val hasil = GeoOffline.validasi(
            HasilLokasi(tersedia = true, lat = titikLat, lng = titikLng),
            KonfigLokasi(titikLat, titikLng, 100),
        )
        assertTrue(hasil.valid)
        assertTrue(hasil.jarakMeter!! < 1.0)
        assertTrue(hasil.alasan.contains("[offline]"))
    }

    @Test
    fun `validasi - di luar radius - ditolak dengan jarak`() {
        val hasil = GeoOffline.validasi(
            HasilLokasi(tersedia = true, lat = titikLat + 0.01, lng = titikLng),
            KonfigLokasi(titikLat, titikLng, 50),
        )
        assertFalse(hasil.valid)
        assertTrue(hasil.jarakMeter!! > 1000.0)
        assertTrue(hasil.alasan.contains("di luar radius"))
    }
}
