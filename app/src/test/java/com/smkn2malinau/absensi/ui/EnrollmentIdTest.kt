package com.smkn2malinau.absensi.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentIdTest {

    @Test
    fun `id enroll lokal selalu negatif`() {
        listOf("23001", "0", "abc", "999999999999", "", "  9  ").forEach {
            assertTrue("id untuk '$it' harus < 0", EnrollmentViewModel.idEnrollLokal(it) < 0)
        }
    }

    @Test
    fun `id enroll lokal deterministik per NIS`() {
        assertEquals(
            EnrollmentViewModel.idEnrollLokal("23001"),
            EnrollmentViewModel.idEnrollLokal(" 23001 ")
        )
    }

    @Test
    fun `NIS berbeda umumnya menghasilkan id berbeda`() {
        val a = EnrollmentViewModel.idEnrollLokal("23001")
        val b = EnrollmentViewModel.idEnrollLokal("23002")
        assertTrue(a != b)
    }
}
