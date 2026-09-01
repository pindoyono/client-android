package com.smkn2malinau.absensi

import com.smkn2malinau.absensi.validation.Validation
import org.junit.Assert.*
import org.junit.Test

/**
 * Test validasi input — port dari app/validation.py Windows (PRD 9.5).
 */
class ValidationTest {

    @Test
    fun `nis valid`() {
        assertTrue(Validation.isValidNis("23145"))
        assertTrue(Validation.isValidNis("1234567890"))
    }

    @Test
    fun `nis invalid`() {
        assertFalse(Validation.isValidNis("123"))
        assertFalse(Validation.isValidNis("abc"))
        assertFalse(Validation.isValidNis(""))
    }

    @Test
    fun `nama valid`() {
        assertTrue(Validation.isValidNama("Ahmad Fauzan"))
        assertTrue(Validation.isValidNama("Siti Nurhaliza"))
    }

    @Test
    fun `nama invalid`() {
        assertFalse(Validation.isValidNama("A"))
        assertFalse(Validation.isValidNama("12345"))
        assertFalse(Validation.isValidNama(""))
    }

    @Test
    fun `kelas valid`() {
        assertTrue(Validation.isValidKelas("XI Elektronika"))
        assertTrue(Validation.isValidKelas("XII-A"))
    }

    @Test
    fun `device id valid`() {
        assertTrue(Validation.isValidDeviceId("device_android_01"))
        assertTrue(Validation.isValidDeviceId("ABC-123_xyz"))
    }

    @Test
    fun `device id invalid`() {
        assertFalse(Validation.isValidDeviceId("ab"))
        assertFalse(Validation.isValidDeviceId(""))
    }

    @Test
    fun `api key valid`() {
        assertTrue(Validation.isValidApiKey("abcdefghijklmnopqrstuvwxyz123456"))
    }

    @Test
    fun `api key invalid`() {
        assertFalse(Validation.isValidApiKey("short"))
        assertFalse(Validation.isValidApiKey(""))
    }
}
