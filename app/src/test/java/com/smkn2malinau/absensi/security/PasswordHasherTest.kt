package com.smkn2malinau.absensi.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `hash lalu verifikasi - password benar lolos`() {
        val h = PasswordHasher.hash("rahasia123")
        assertTrue(PasswordHasher.verifikasi("rahasia123", h.hashB64, h.saltB64))
    }

    @Test
    fun `password salah - ditolak`() {
        val h = PasswordHasher.hash("rahasia123")
        assertFalse(PasswordHasher.verifikasi("rahasia124", h.hashB64, h.saltB64))
    }

    @Test
    fun `salt acak - hash beda untuk password sama`() {
        assertNotEquals(PasswordHasher.hash("sama").hashB64, PasswordHasher.hash("sama").hashB64)
    }

    @Test
    fun `hash atau salt null - verifikasi false`() {
        assertFalse(PasswordHasher.verifikasi("apa", null, "x"))
        assertFalse(PasswordHasher.verifikasi("apa", "x", null))
        assertFalse(PasswordHasher.verifikasi("apa", "", ""))
    }
}
