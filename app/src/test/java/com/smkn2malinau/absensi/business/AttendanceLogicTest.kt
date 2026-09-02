package com.smkn2malinau.absensi.business

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class AttendanceLogicTest {

    private val logic = AttendanceLogic()
    private val standardJadwal = AttendanceLogic.JadwalEfektif(
        jamMasuk = LocalTime.of(7, 0), // 07:00
        jamPulang = LocalTime.of(14, 0) // 14:00
    )

    @Test
    fun `Scan pertama dalam jendela 2 jam - NORMAL`() {
        val sekarang = LocalTime.of(6, 30) // masuk - 30m
        val status = AttendanceLogic.StatusAbsensi(sudahMasuk = false, sudahPulang = false)
        
        val hasil = logic.hitungHasil(sekarang, standardJadwal, status)
        assertEquals(HasilAbsen.BERHASIL_MASUK_NORMAL, hasil)
    }

    @Test
    fun `Scan pertama sebelum jendela 2 jam - DITOLAK`() {
        val sekarang = LocalTime.of(4, 59) // masuk - 2j 1m
        val status = AttendanceLogic.StatusAbsensi(sudahMasuk = false, sudahPulang = false)
        
        val hasil = logic.hitungHasil(sekarang, standardJadwal, status)
        assertEquals(HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK, hasil)
    }

    @Test
    fun `Scan pertama lewat toleransi 5 menit - TERLAMBAT`() {
        val sekarang = LocalTime.of(7, 6) // masuk + 6m
        val status = AttendanceLogic.StatusAbsensi(sudahMasuk = false, sudahPulang = false)
        
        val hasil = logic.hitungHasil(sekarang, standardJadwal, status)
        assertEquals(HasilAbsen.BERHASIL_MASUK_TERLAMBAT, hasil)
    }

    @Test
    fun `Scan kedua sebelum jam pulang tanpa dispensasi - DITOLAK`() {
        val sekarang = LocalTime.of(13, 0) // pulang - 1j
        val status = AttendanceLogic.StatusAbsensi(sudahMasuk = true, sudahPulang = false)
        
        val hasil = logic.hitungHasil(sekarang, standardJadwal, status)
        assertEquals(HasilAbsen.DITOLAK_BELUM_WAKTUNYA_PULANG, hasil)
    }

    @Test
    fun `Scan kedua sebelum jam pulang ada dispensasi - BERHASIL CEAPAT`() {
        val sekarang = LocalTime.of(13, 0)
        val status = AttendanceLogic.StatusAbsensi(sudahMasuk = true, sudahPulang = false)
        
        val hasil = logic.hitungHasil(sekarang, standardJadwal, status, adaDispensasiPulangCepat = true)
        assertEquals(HasilAbsen.BERHASIL_PULANG_CEPAT, hasil)
    }

    @Test
    fun `Scan kedua setelah jam pulang - NORMAL`() {
        val sekarang = LocalTime.of(14, 1)
        val status = AttendanceLogic.StatusAbsensi(sudahMasuk = true, sudahPulang = false)
        
        val hasil = logic.hitungHasil(sekarang, standardJadwal, status)
        assertEquals(HasilAbsen.BERHASIL_PULANG_NORMAL, hasil)
    }

    @Test
    fun `Scan ketiga hari yang sama - DITOLAK`() {
        val sekarang = LocalTime.of(15, 0)
        val status = AttendanceLogic.StatusAbsensi(sudahMasuk = true, sudahPulang = true)
        
        val hasil = logic.hitungHasil(sekarang, standardJadwal, status)
        assertEquals(HasilAbsen.DITOLAK_SUDAH_ABSEN_LENGKAP, hasil)
    }
}
