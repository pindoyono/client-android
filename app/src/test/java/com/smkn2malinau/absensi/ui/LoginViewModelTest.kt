package com.smkn2malinau.absensi.ui

import com.smkn2malinau.absensi.MainDispatcherRule
import com.smkn2malinau.absensi.auth.AuthRepository
import com.smkn2malinau.absensi.auth.HasilLogin
import com.smkn2malinau.absensi.auth.Role
import com.smkn2malinau.absensi.auth.SesiPengguna
import com.smkn2malinau.absensi.device.GoogleIdTokenProvider
import com.smkn2malinau.absensi.ui.auth.LoginViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = mockk<AuthRepository>(relaxed = true)
    private val google = mockk<GoogleIdTokenProvider>(relaxed = true).also {
        every { it.terkonfigurasi } returns false
    }

    @Test
    fun `reset mengosongkan email dan password`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val vm = LoginViewModel(auth, google)
        vm.onIdentitas("mcnan")
        vm.onPassword("Mcnan501234")

        vm.reset()

        assertEquals("", vm.uiState.value.identitas)
        assertEquals("", vm.uiState.value.password)
        assertEquals(false, vm.uiState.value.butuhBuatPassword)
    }

    @Test
    fun `login offline sukses mengosongkan form`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        coEvery { auth.adaAkun() } returns true
        coEvery { auth.loginPassword(any(), any()) } returns
            HasilLogin.Sukses(SesiPengguna("mcnan", "Mcnan", Role.ADMIN))
        val vm = LoginViewModel(auth, google)
        vm.onIdentitas("mcnan")
        vm.onPassword("Mcnan501234")

        var lolos = false
        vm.loginPassword { lolos = true }
        advanceUntilIdle()

        assertTrue(lolos)
        assertEquals("", vm.uiState.value.identitas)
        assertEquals("", vm.uiState.value.password)
    }
}
