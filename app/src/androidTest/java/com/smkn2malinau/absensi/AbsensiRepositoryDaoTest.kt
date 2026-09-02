package com.smkn2malinau.absensi

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smkn2malinau.absensi.business.HasilAbsen
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.data.local.entity.DispensasiCache
import com.smkn2malinau.absensi.data.local.entity.JadwalCache
import com.smkn2malinau.absensi.data.local.entity.JadwalOverrideLokal
import com.smkn2malinau.absensi.repository.AbsensiRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * PRD bagian 13 — test Repository/DAO: jadwalEfektif, statusHariIni, constraint UNIQUE.
 * Room in-memory (tanpa SQLCipher) supaya cepat & deterministik.
 */
@RunWith(AndroidJUnit4::class)
class AbsensiRepositoryDaoTest {

    private lateinit var db: AbsensiDatabase
    private lateinit var repo: AbsensiRepositoryImpl
    private val hariIni = LocalDate.now().toString()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AbsensiDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = AbsensiRepositoryImpl(db, "device_test")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun constraint_unique_siswa_tanggal_type() = runBlocking {
        val ok1 = repo.simpanAbsensi(1, HasilAbsen.BERHASIL_MASUK_NORMAL, "NORMAL", null)
        val ok2 = repo.simpanAbsensi(1, HasilAbsen.BERHASIL_MASUK_TERLAMBAT, "TERLAMBAT", null)
        val okPulang = repo.simpanAbsensi(1, HasilAbsen.BERHASIL_PULANG_NORMAL, "NORMAL", null)

        assertTrue(ok1)
        assertFalse("MASUK kedua di hari & siswa sama harus ditolak UNIQUE", ok2)
        assertTrue("PULANG di hari sama masih boleh (type beda)", okPulang)

        assertEquals(1, db.absensiDao().getAbsensiHariIni(1, hariIni).count { it.type == "MASUK" })
    }

    @Test
    fun jadwalEfektif_pakai_override_lokal_kalau_ada() = runBlocking {
        db.jadwalDao().insertJadwal(
            listOf(JadwalCache("XI-E", hariIni, "SELASA", "07:00", "14:00", "standar", "now"))
        )
        db.jadwalDao().insertOverride(
            JadwalOverrideLokal(
                id = "o1", tanggal = hariIni, kelas = "XI-E",
                jam_masuk = "09:00", jam_pulang = "12:00", alasan = "upacara",
                dibuat_pada = "2026-09-02T06:00:00"
            )
        )

        val jadwal = repo.jadwalEfektif("XI-E", hariIni)

        assertNotNull(jadwal)
        assertEquals(LocalTime.of(9, 0), jadwal!!.jamMasuk)
        assertEquals(LocalTime.of(12, 0), jadwal.jamPulang)
    }

    @Test
    fun jadwalEfektif_fallback_ke_jadwal_cache_server() = runBlocking {
        db.jadwalDao().insertJadwal(
            listOf(JadwalCache("XI-E", hariIni, "SELASA", "07:00", "14:00", "standar", "now"))
        )

        val jadwal = repo.jadwalEfektif("XI-E", hariIni)

        assertEquals(LocalTime.of(7, 0), jadwal!!.jamMasuk)
        assertEquals(LocalTime.of(14, 0), jadwal.jamPulang)
    }

    @Test
    fun jadwalEfektif_null_kalau_tidak_ada_data() = runBlocking {
        assertNull(repo.jadwalEfektif("XII-A", hariIni))
    }

    @Test
    fun statusHariIni_deteksi_sudah_masuk_belum_pulang() = runBlocking {
        db.absensiDao().insertAbsensi(
            AbsensiLokal(
                record_id = "r1", siswa_id = 5, tanggal = hariIni, type = "MASUK",
                jam_aktual = "06:45:00", status_kehadiran_otomatis = "NORMAL", catatan = "",
                device_id = "d", dibuat_pada = "x"
            )
        )

        val status = repo.statusHariIni(5, hariIni)

        assertTrue(status.sudahMasuk)
        assertFalse(status.sudahPulang)
        assertEquals(LocalTime.of(6, 45, 0), status.jamMasukAktual)
    }

    @Test
    fun dispensasiAktif_utamakan_jenis_pulang_cepat() = runBlocking {
        db.jadwalDao().insertDispensasi(
            listOf(
                DispensasiCache(5, hariIni, "LAINNYA", "IZIN", "a"),
                DispensasiCache(5, hariIni, "PULANG_CEPAT", "SAKIT", "demam"),
            )
        )

        val d = repo.dispensasiAktif(5, hariIni)

        assertEquals("PULANG_CEPAT", d!!.jenis)
        assertEquals("SAKIT", d.kategori)
    }
}
