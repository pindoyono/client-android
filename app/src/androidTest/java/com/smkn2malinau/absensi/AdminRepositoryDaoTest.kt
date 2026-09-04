package com.smkn2malinau.absensi

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smkn2malinau.absensi.business.HasilAbsen
import com.smkn2malinau.absensi.data.local.AbsensiDatabase
import com.smkn2malinau.absensi.data.local.entity.EmbeddingCache
import com.smkn2malinau.absensi.data.local.entity.JadwalCache
import com.smkn2malinau.absensi.data.local.entity.SiswaCache
import com.smkn2malinau.absensi.repository.AbsensiRepositoryImpl
import com.smkn2malinau.absensi.repository.AdminRepositoryImpl
import com.smkn2malinau.absensi.ui.EnrollmentViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * PRD paritas Windows — Panel Admin: statistik sync, kelola override lokal, data siswa.
 */
@RunWith(AndroidJUnit4::class)
class AdminRepositoryDaoTest {

    private lateinit var db: AbsensiDatabase
    private lateinit var admin: AdminRepositoryImpl
    private lateinit var kiosk: AbsensiRepositoryImpl
    private val hariIni = LocalDate.now().toString()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AbsensiDatabase::class.java
        ).allowMainThreadQueries().build()
        admin = AdminRepositoryImpl(db)
        kiosk = AbsensiRepositoryImpl(db, "device_test")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun statistikSync_menghitung_menunggu_dan_tersinkron() = runBlocking {
        kiosk.simpanAbsensi(1, HasilAbsen.BERHASIL_MASUK_NORMAL, "NORMAL", null)   // menunggu
        kiosk.simpanAbsensi(2, HasilAbsen.BERHASIL_MASUK_NORMAL, "NORMAL", null)   // menunggu
        // tandai satu tersinkron
        val antri = db.absensiDao().getAntrianSync()
        db.absensiDao().updateAbsensi(antri.first().copy(synced = 1, sync_status = "ok"))

        val s = admin.statistikSync()
        assertEquals(2, s.totalAbsensi)
        assertEquals(1, s.tersinkron)
        assertEquals(1, s.menunggu)
        assertEquals(0, s.gagal)
        assertEquals(50, s.persenTersinkron)
    }

    @Test
    fun override_lokal_langsung_dipakai_jadwalEfektif_offline() = runBlocking {
        db.jadwalDao().insertJadwal(
            listOf(JadwalCache("XI-E", hariIni, "SELASA", "07:00:00", "15:00:00", "standar", "now"))
        )
        admin.simpanOverrideLokal(hariIni, "XI-E", "09:00", "12:00", "upacara")

        val jadwal = kiosk.jadwalEfektif("XI-E", hariIni)
        assertEquals(java.time.LocalTime.of(9, 0), jadwal!!.jamMasuk)
        assertEquals(java.time.LocalTime.of(12, 0), jadwal.jamPulang)

        // hapus → kembali ke jadwal server
        val ov = admin.overrideLokalSemua().first()
        admin.hapusOverrideLokal(ov.id)
        assertEquals(java.time.LocalTime.of(7, 0), kiosk.jadwalEfektif("XI-E", hariIni)!!.jamMasuk)
    }

    @Test
    fun resetOverrideDitolak_hanya_yang_ditolak() = runBlocking {
        admin.simpanOverrideLokal(hariIni, null, "08:00", "13:00", null)
        val ov = admin.overrideLokalSemua().first()
        db.jadwalDao().updateOverrideLokal(ov.copy(terkirim = 1, status_push = "ditolak", pesan_push = "kelas invalid"))

        val n = admin.resetOverrideDitolak()
        assertEquals(1, n)
        val setelah = admin.overrideLokalSemua().first()
        assertEquals("pending", setelah.status_push)
        assertEquals(0, setelah.terkirim)
    }

    @Test
    fun daftarSiswaLokal_flag_enroll_dan_lokal() = runBlocking {
        db.siswaDao().insertSiswa(listOf(SiswaCache(5, "23005", "Sinta", "XI-E")))
        db.siswaDao().insertEmbedding(
            listOf(EmbeddingCache(5, ByteArray(8), "arcface-v1", "now"))
        )
        // enroll lokal (id negatif, belum ada embedding)
        db.siswaDao().insertSiswa(listOf(SiswaCache(-99, "23099", "Lokal", "XI-E")))

        val rows = admin.daftarSiswaLokal().associateBy { it.siswaId }
        assertTrue(rows.getValue(5).terEnroll)
        assertFalse(rows.getValue(5).lokal)
        assertFalse(rows.getValue(-99).terEnroll)
        assertTrue(rows.getValue(-99).lokal)
    }

    @Test
    fun sync_server_menimpa_enroll_lokal_dengan_nis_sama() = runBlocking {
        val idLokal = EnrollmentViewModel.idEnrollLokal("23005")
        db.siswaDao().insertSiswa(listOf(SiswaCache(idLokal, "23005", "Sinta Lokal", "XI-E")))
        db.siswaDao().insertEmbedding(listOf(EmbeddingCache(idLokal, ByteArray(8), "arcface-local", "now")))

        // server sync: baris versi server di-insert dulu, lalu bersihkan enroll lokal ber-NIS sama
        db.siswaDao().insertSiswa(listOf(SiswaCache(5, "23005", "Sinta", "XI-E")))
        db.siswaDao().hapusEmbeddingEnrollLokalTertimpa()
        db.siswaDao().hapusSiswaEnrollLokalTertimpa()

        val rows = admin.daftarSiswaLokal()
        assertEquals(1, rows.size)
        assertEquals(5, rows.first().siswaId)
    }
}
