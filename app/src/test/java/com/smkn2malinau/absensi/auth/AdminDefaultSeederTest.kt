package com.smkn2malinau.absensi.auth

import com.smkn2malinau.absensi.data.local.dao.AkunDao
import com.smkn2malinau.absensi.data.local.entity.AkunLokal
import com.smkn2malinau.absensi.security.PasswordHasher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminDefaultSeederTest {

    private class FakeAkunDao : AkunDao {
        val rows = mutableMapOf<String, AkunLokal>()
        override suspend fun getByIdentitas(identitas: String) = rows[identitas]?.takeIf { it.aktif == 1 }
        override suspend fun getByIdentitasApaPun(identitas: String) = rows[identitas]
        override suspend fun getSemua() = rows.values.filter { it.aktif == 1 }
        override suspend fun upsert(akun: AkunLokal) { rows[akun.identitas] = akun }
        override suspend fun nonaktifkan(identitas: String, waktu: String) {}
        override suspend fun setPassword(identitas: String, hash: String, salt: String, waktu: String) {}
        override suspend fun countAdminAktif() = rows.values.count { it.role == "admin" && it.aktif == 1 }
        override suspend fun countAktif() = rows.values.count { it.aktif == 1 }
    }

    @Test
    fun `seed membuat akun admin baru dengan password valid`() = runTest {
        val dao = FakeAkunDao()
        dao.seedAdminDefault("Mcnan", "rahasia123")

        val akun = dao.rows["mcnan"]!!  // identitas di-lowercase
        assertEquals("admin", akun.role)
        assertEquals("Mcnan", akun.nama)
        assertTrue(PasswordHasher.verifikasi("rahasia123", akun.password_hash, akun.salt))
        assertEquals(1, akun.harus_ganti_sandi)  // password baku → wajib ganti saat login
    }

    @Test
    fun `tidak menimpa akun yang sudah ada`() = runTest {
        val dao = FakeAkunDao()
        dao.rows["mcnan"] = AkunLokal("mcnan", "Mcnan", "admin", "HASH_LAMA", "SALT_LAMA", null, 1, "x")

        dao.seedAdminDefault("Mcnan", "passwordbaru")

        assertEquals("HASH_LAMA", dao.rows["mcnan"]!!.password_hash)
    }

    @Test
    fun `user kosong atau password pendek - tidak seed`() = runTest {
        val dao = FakeAkunDao()
        dao.seedAdminDefault("", "rahasia123")
        dao.seedAdminDefault("Mcnan", "12345")  // < 6
        assertNull(dao.rows["mcnan"])
        assertTrue(dao.rows.isEmpty())
    }
}
