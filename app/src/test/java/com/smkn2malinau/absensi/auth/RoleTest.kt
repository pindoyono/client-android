package com.smkn2malinau.absensi.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleTest {

    @Test
    fun `mapping string role dari server`() {
        assertEquals(Role.ADMIN, Role.dari("admin"))
        assertEquals(Role.GURU_PIKET, Role.dari("guru_piket"))
        assertEquals(Role.GURU_PIKET, Role.dari("Guru Piket"))
        assertEquals(Role.SISWA, Role.dari("siswa"))
        assertEquals(Role.SISWA, Role.dari(null))
        assertEquals(Role.SISWA, Role.dari("tidak_dikenal"))
    }

    @Test
    fun `hak akses admin - semua fitur`() {
        Fitur.entries.forEach { assertTrue("admin harus boleh $it", Role.ADMIN.boleh(it)) }
    }

    @Test
    fun `hak akses guru piket - jadwal sinkron data siswa daftar wajah saja`() {
        assertTrue(Role.GURU_PIKET.boleh(Fitur.PANEL_ADMIN))
        assertTrue(Role.GURU_PIKET.boleh(Fitur.JADWAL))
        assertTrue(Role.GURU_PIKET.boleh(Fitur.SINKRONISASI))
        assertTrue(Role.GURU_PIKET.boleh(Fitur.DATA_SISWA))
        assertTrue(Role.GURU_PIKET.boleh(Fitur.DAFTAR_WAJAH))
        assertFalse(Role.GURU_PIKET.boleh(Fitur.PENGATURAN))
        assertFalse(Role.GURU_PIKET.boleh(Fitur.KELOLA_AKUN))
    }

    @Test
    fun `hak akses siswa - hanya riwayat sendiri`() {
        assertTrue(Role.SISWA.boleh(Fitur.RIWAYAT_SENDIRI))
        assertFalse(Role.SISWA.boleh(Fitur.PANEL_ADMIN))
        assertFalse(Role.SISWA.boleh(Fitur.DATA_SISWA))
    }
}
