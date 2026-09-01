package com.smkn2malinau.absensi

import com.smkn2malinau.absensi.business.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Test tabel skenario PRD bagian 3.4 — murni JVM, tanpa kamera/emulator.
 */
class AttendanceLogicTest {

    private val tanggal = LocalDate.of(2024, 1, 15).toString()
    private val jadwal = JadwalEfektif(
        jamMasuk = LocalTime.of(7, 0),
        jamPulang = LocalTime.of(15, 0),
        sumber = SumberJadwal.JADWAL_STANDAR
    )

    // Skenario 1: Scan pertama, dalam jendela 2 jam → BERHASIL_MASUK (NORMAL)
    @Test
    fun `scan pertama dalam jendela - berhasil masuk normal`() {
        val waktu = LocalDateTime.of(2024, 1, 15, 6, 30)
        val hasil = AttendanceLogic.prosesScan(
            records = emptyList(),
            jadwalEfektif = jadwal,
            dispensasi = null,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.BERHASIL_MASUK, hasil.hasil)
        assertEquals(KategoriWaktu.NORMAL, hasil.kategoriWaktu)
    }

    // Skenario 2: Scan pertama, sebelum jendela → DITOLAK_BELUM_WAKTUNYA_MASUK
    @Test
    fun `scan pertama sebelum jendela - ditolak belum waktunya`() {
        val waktu = LocalDateTime.of(2024, 1, 15, 4, 59) // sebelum 05:00
        val hasil = AttendanceLogic.prosesScan(
            records = emptyList(),
            jadwalEfektif = jadwal,
            dispensasi = null,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.DITOLAK_BELUM_WAKTUNYA_MASUK, hasil.hasil)
    }

    // Skenario 3: Scan pertama, lewat toleransi 5 menit → BERHASIL_MASUK (TERLAMBAT)
    @Test
    fun `scan pertama lewat toleransi - berhasil masuk terlambat`() {
        val waktu = LocalDateTime.of(2024, 1, 15, 7, 6) // 7:06 > 7:05
        val hasil = AttendanceLogic.prosesScan(
            records = emptyList(),
            jadwalEfektif = jadwal,
            dispensasi = null,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.BERHASIL_MASUK, hasil.hasil)
        assertEquals(KategoriWaktu.TERLAMBAT, hasil.kategoriWaktu)
    }

    // Skenario 4: Scan kedua, sebelum jam pulang, tanpa dispensasi → DITOLAK_BELUM_WAKTUNYA_PULANG
    @Test
    fun `scan kedua sebelum pulang tanpa dispensasi - ditolak`() {
        val records = listOf(
            RecordAbsen(
                recordId = "r1", siswaId = 1, tanggal = tanggal, type = "MASUK",
                jamAktual = "06:30", statusKehadiranOtomatis = "NORMAL", catatan = null
            )
        )
        val waktu = LocalDateTime.of(2024, 1, 15, 14, 0) // sebelum 15:00
        val hasil = AttendanceLogic.prosesScan(
            records = records,
            jadwalEfektif = jadwal,
            dispensasi = null,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.DITOLAK_BELUM_WAKTUNYA_PULANG, hasil.hasil)
    }

    // Skenario 5: Scan kedua, sebelum jam pulang, ada dispensasi → BERHASIL_PULANG (DISPENSASI)
    @Test
    fun `scan kedua sebelum pulang dengan dispensasi - berhasil pulang`() {
        val records = listOf(
            RecordAbsen(
                recordId = "r1", siswaId = 1, tanggal = tanggal, type = "MASUK",
                jamAktual = "06:30", statusKehadiranOtomatis = "NORMAL", catatan = null
            )
        )
        val dispensasi = DispensasiAktif(
            siswaId = 1, tanggal = tanggal, jenis = "PULANG_CEPAT",
            kategori = "SAKIT", alasan = "Sakit"
        )
        val waktu = LocalDateTime.of(2024, 1, 15, 14, 0)
        val hasil = AttendanceLogic.prosesScan(
            records = records,
            jadwalEfektif = jadwal,
            dispensasi = dispensasi,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.BERHASIL_PULANG, hasil.hasil)
        assertEquals(KategoriWaktu.DISPENSASI, hasil.kategoriWaktu)
        assertEquals("SAKIT", hasil.kategoriDispensasi)
    }

    // Skenario 6: Scan kedua, setelah jam pulang → BERHASIL_PULANG (NORMAL)
    @Test
    fun `scan kedua setelah pulang - berhasil pulang normal`() {
        val records = listOf(
            RecordAbsen(
                recordId = "r1", siswaId = 1, tanggal = tanggal, type = "MASUK",
                jamAktual = "06:30", statusKehadiranOtomatis = "NORMAL", catatan = null
            )
        )
        val waktu = LocalDateTime.of(2024, 1, 15, 15, 30)
        val hasil = AttendanceLogic.prosesScan(
            records = records,
            jadwalEfektif = jadwal,
            dispensasi = null,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.BERHASIL_PULANG, hasil.hasil)
        assertEquals(KategoriWaktu.NORMAL, hasil.kategoriWaktu)
    }

    // Skenario 7: Scan ketiga hari sama → DITOLAK_SUDAH_ABSEN
    @Test
    fun `scan ketiga hari sama - ditolak sudah absen`() {
        val records = listOf(
            RecordAbsen(
                recordId = "r1", siswaId = 1, tanggal = tanggal, type = "MASUK",
                jamAktual = "06:30", statusKehadiranOtomatis = "NORMAL", catatan = null
            ),
            RecordAbsen(
                recordId = "r2", siswaId = 1, tanggal = tanggal, type = "PULANG",
                jamAktual = "15:30", statusKehadiranOtomatis = "NORMAL", catatan = null
            )
        )
        val waktu = LocalDateTime.of(2024, 1, 15, 16, 0)
        val hasil = AttendanceLogic.prosesScan(
            records = records,
            jadwalEfektif = jadwal,
            dispensasi = null,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.DITOLAK_SUDAH_ABSEN, hasil.hasil)
    }

    // Skenario 8: Hari berikutnya → reset, boleh MASUK lagi
    @Test
    fun `hari berikutnya - reset dan boleh masuk`() {
        val records = listOf(
            RecordAbsen(
                recordId = "r1", siswaId = 1, tanggal = tanggal, type = "MASUK",
                jamAktual = "06:30", statusKehadiranOtomatis = "NORMAL", catatan = null
            ),
            RecordAbsen(
                recordId = "r2", siswaId = 1, tanggal = tanggal, type = "PULANG",
                jamAktual = "15:30", statusKehadiranOtomatis = "NORMAL", catatan = null
            )
        )
        // Hari berikutnya — records kosong (query per tanggal)
        val waktu = LocalDateTime.of(2024, 1, 16, 6, 30)
        val hasil = AttendanceLogic.prosesScan(
            records = emptyList(),
            jadwalEfektif = jadwal,
            dispensasi = null,
            waktuSekarang = waktu
        )
        assertEquals(HasilAbsen.BERHASIL_MASUK, hasil.hasil)
    }

    // Test tambahan: override lokal menang atas override server
    @Test
    fun `override lokal menang atas override server`() {
        val overridesLokal = listOf(
            OverrideJadwal(
                id = "local1", tanggal = tanggal, kelas = "XI-E",
                jamMasuk = "08:00", jamPulang = "16:00", alasan = "Acara sekolah"
            )
        )
        val overridesServer = listOf(
            OverrideJadwal(
                id = "server1", tanggal = tanggal, kelas = "XI-E",
                jamMasuk = "06:00", jamPulang = "14:00", alasan = null
            )
        )
        val jadwalStandar = JadwalStandar(jamMasuk = "07:00", jamPulang = "15:00")

        val hasil = AttendanceLogic.resolusiJadwal(
            tanggal = tanggal,
            kelas = "XI-E",
            overridesLokal = overridesLokal,
            overridesServer = overridesServer,
            jadwalStandar = jadwalStandar
        )

        assertNotNull(hasil)
        assertEquals(LocalTime.of(8, 0), hasil!!.jamMasuk)
        assertEquals(SumberJadwal.OVERRIDE_LOKAL, hasil.sumber)
    }

    // Test: override server menang atas jadwal standar
    @Test
    fun `override server menang atas jadwal standar`() {
        val overridesServer = listOf(
            OverrideJadwal(
                id = "server1", tanggal = tanggal, kelas = "XI-E",
                jamMasuk = "06:00", jamPulang = "14:00", alasan = null
            )
        )
        val jadwalStandar = JadwalStandar(jamMasuk = "07:00", jamPulang = "15:00")

        val hasil = AttendanceLogic.resolusiJadwal(
            tanggal = tanggal,
            kelas = "XI-E",
            overridesLokal = emptyList(),
            overridesServer = overridesServer,
            jadwalStandar = jadwalStandar
        )

        assertNotNull(hasil)
        assertEquals(LocalTime.of(6, 0), hasil!!.jamMasuk)
        assertEquals(SumberJadwal.OVERRIDE_SERVER, hasil.sumber)
    }

    // Test: safety gate — mode testing tidak simpan
    @Test
    fun `mode testing - kenali tapi jangan simpan`() {
        assertFalse(AttendanceLogic.bolehSimpan(HasilAbsen.BERHASIL_MASUK, onSiteTestingSelesai = false))
        assertTrue(AttendanceLogic.bolehSimpan(HasilAbsen.BERHASIL_MASUK, onSiteTestingSelesai = true))
        assertFalse(AttendanceLogic.bolehSimpan(HasilAbsen.DITOLAK_SUDAH_ABSEN, onSiteTestingSelesai = true))
    }
}
