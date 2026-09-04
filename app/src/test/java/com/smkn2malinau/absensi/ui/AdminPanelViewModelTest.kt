package com.smkn2malinau.absensi.ui

import com.smkn2malinau.absensi.MainDispatcherRule
import com.smkn2malinau.absensi.data.local.entity.AbsensiLokal
import com.smkn2malinau.absensi.data.local.entity.JadwalCache
import com.smkn2malinau.absensi.data.local.entity.JadwalOverrideLokal
import com.smkn2malinau.absensi.repository.AdminRepository
import com.smkn2malinau.absensi.repository.SiswaLokalRow
import com.smkn2malinau.absensi.repository.StatistikSync
import com.smkn2malinau.absensi.security.CredentialManager
import com.smkn2malinau.absensi.ui.admin.AdminPanelViewModel
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminPanelViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cm = mockk<CredentialManager>(relaxed = true).also {
        every { it.getServerBaseUrl() } returns null
        every { it.lensaKameraDepan() } returns true
    }
    private val wm = mockk<WorkManager>().also {
        every { it.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
    }

    private fun vm(repo: AdminRepository) =
        AdminPanelViewModel(repo, cm, wm, serverUrlDefault = "https://absen.test/")

    @Test
    fun `refresh mengisi statistik dan daftar`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repo = FakeAdminRepo(
            stat = StatistikSync(10, 6, 3, 1, 5, 2, 0, "2026-09-03T08:00:00")
        )
        val vm = vm(repo)
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(10, s.stat?.totalAbsensi)
        assertEquals(60, s.stat?.persenTersinkron)
        assertEquals(1, s.siswa.size)
    }

    @Test
    fun `tambah override jam tidak valid - tolak, tidak panggil repo`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repo = FakeAdminRepo()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.tambahOverrideLokal("2026-09-03", "XI-E", "7", "15:00", "")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pesanError)
        assertEquals(0, repo.overrideDisimpan.size)
    }

    @Test
    fun `tambah override valid - panggil repo lalu refresh`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repo = FakeAdminRepo()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.tambahOverrideLokal("2026-09-03", "", "09:00", "12:00", "upacara")
        advanceUntilIdle()

        assertEquals(1, repo.overrideDisimpan.size)
        assertEquals("09:00" to "12:00", repo.overrideDisimpan[0].let { it.jamMasuk to it.jamPulang })
        assertEquals(null, repo.overrideDisimpan[0].kelas)
        assertTrue(!vm.uiState.value.pesanError)
    }

    @Test
    fun `hapus override memanggil repo`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repo = FakeAdminRepo()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.hapusOverrideLokal("o1")
        advanceUntilIdle()

        assertEquals(listOf("o1"), repo.overrideDihapus)
    }

    @Test
    fun `reset push - tidak ada yang ditolak - pesan error`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repo = FakeAdminRepo(resetCount = 0)
        val vm = vm(repo)
        advanceUntilIdle()

        vm.resetPushDitolak()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pesanError)
    }

    data class OverrideDibuat(val kelas: String?, val jamMasuk: String, val jamPulang: String)

    private class FakeAdminRepo(
        private val stat: StatistikSync = StatistikSync(0, 0, 0, 0, 0, 0, 0, null),
        private val resetCount: Int = 3,
    ) : AdminRepository {
        val overrideDisimpan = mutableListOf<OverrideDibuat>()
        val overrideDihapus = mutableListOf<String>()

        override suspend fun statistikSync() = stat
        override suspend fun recordSyncTerbaru(limit: Int): List<AbsensiLokal> = emptyList()
        override suspend fun jadwalStandar(): List<JadwalCache> = emptyList()
        override suspend fun overrideLokalSemua(): List<JadwalOverrideLokal> = emptyList()
        override suspend fun simpanOverrideLokal(tanggal: String, kelas: String?, jamMasuk: String, jamPulang: String, alasan: String?) {
            overrideDisimpan.add(OverrideDibuat(kelas, jamMasuk, jamPulang))
        }
        override suspend fun hapusOverrideLokal(id: String) { overrideDihapus.add(id) }
        override suspend fun resetOverrideDitolak(): Int = resetCount
        override suspend fun daftarSiswaLokal(): List<SiswaLokalRow> =
            listOf(SiswaLokalRow(1, "23001", "Budi", "XI-E", terEnroll = true, lokal = false))
        override suspend fun tesDekripsiEmbedding(faceKey: String): Pair<Int, Int> = 0 to 0
    }
}
