package com.smkn2malinau.absensi.ui

import com.smkn2malinau.absensi.MainDispatcherRule
import com.smkn2malinau.absensi.business.AttendanceLogic
import com.smkn2malinau.absensi.business.HasilAbsen
import com.smkn2malinau.absensi.data.local.entity.DispensasiCache
import com.smkn2malinau.absensi.face.FaceEngine
import com.smkn2malinau.absensi.face.HasilDeteksiWajah
import com.smkn2malinau.absensi.face.LivenessResult
import com.smkn2malinau.absensi.repository.AbsensiRepository
import com.smkn2malinau.absensi.repository.SiswaCocok
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Test E2E jalur absensi (PRD bagian 4, 5, 6) — FaceEngine & repo di-fake,
 * jadi capture→keputusan→simpan bisa diverifikasi tanpa kamera/DB.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KioskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jadwalStandar = AttendanceLogic.JadwalEfektif(
        jamMasuk = LocalTime.of(7, 0),
        jamPulang = LocalTime.of(14, 0)
    )

    private val embeddingDummy = floatArrayOf(1f, 0f, 0f, 0f)

    private fun deteksiSukses() = HasilDeteksiWajah(
        wajahTerdeteksi = true,
        lolosLiveness = true,
        embedding = embeddingDummy,
        livenessScore = 0.9f,
        ambangLiveness = 0.752f,
        alasanGagal = null
    )

    private fun vm(
        deteksi: HasilDeteksiWajah,
        repo: FakeRepo,
        onSiteTestingSelesai: Boolean,
        jam: LocalTime = LocalTime.of(6, 45),
    ) = KioskViewModel(
        faceEngine = FakeFaceEngine(deteksi),
        attendanceLogic = AttendanceLogic(),
        repo = repo,
        onSiteTestingSelesai = { onSiteTestingSelesai },
        jamProvider = { jam },
        tanggalProvider = { LocalDate.of(2026, 9, 2) },
    )

    @Test
    fun `wajah dikenali dan mode testing SELESAI - absensi tersimpan`() = runTest {
        val repo = FakeRepo(match = cocok(), jadwal = jadwalStandar)
        val vm = vm(deteksiSukses(), repo, onSiteTestingSelesai = true)

        vm.prosesFrame(ByteArray(4))

        assertEquals(1, repo.disimpan.size)
        assertEquals("NORMAL", repo.disimpan[0].status)
        assertEquals(HasilAbsen.BERHASIL_MASUK_NORMAL, repo.disimpan[0].hasil)
        assertEquals(StatusHasil.BERHASIL_TEPAT_WAKTU, vm.uiState.value.hasilTerakhir?.status)
    }

    @Test
    fun `GERBANG uji lapangan - onSiteTestingSelesai FALSE - wajah dikenali tapi TIDAK tersimpan`() = runTest {
        val repo = FakeRepo(match = cocok(), jadwal = jadwalStandar)
        val vm = vm(deteksiSukses(), repo, onSiteTestingSelesai = false)

        vm.prosesFrame(ByteArray(4))

        assertTrue("simpanAbsensi tidak boleh dipanggil di mode testing", repo.disimpan.isEmpty())
        // wajah tetap dikenali (nama & status tetap muncul)
        assertEquals("Budi", vm.uiState.value.hasilTerakhir?.nama)
        assertEquals(StatusHasil.BERHASIL_TEPAT_WAKTU, vm.uiState.value.hasilTerakhir?.status)
    }

    @Test
    fun `wajah tidak dikenali - tidak menyimpan`() = runTest {
        val repo = FakeRepo(match = SiswaCocok(ditemukan = false), jadwal = jadwalStandar)
        val vm = vm(deteksiSukses(), repo, onSiteTestingSelesai = true)

        vm.prosesFrame(ByteArray(4))

        assertTrue(repo.disimpan.isEmpty())
        assertEquals(StatusHasil.WAJAH_TIDAK_DIKENALI, vm.uiState.value.hasilTerakhir?.status)
    }

    @Test
    fun `liveness gagal - tidak menyimpan`() = runTest {
        val repo = FakeRepo(match = cocok(), jadwal = jadwalStandar)
        val deteksi = deteksiSukses().copy(lolosLiveness = false, embedding = null, alasanGagal = "gagal_liveness")
        val vm = vm(deteksi, repo, onSiteTestingSelesai = true)

        vm.prosesFrame(ByteArray(4))

        assertTrue(repo.disimpan.isEmpty())
        assertEquals(StatusHasil.WAJAH_TIDAK_DIKENALI, vm.uiState.value.hasilTerakhir?.status)
    }

    @Test
    fun `sudah absen lengkap - DITOLAK dan tidak menyimpan`() = runTest {
        val repo = FakeRepo(
            match = cocok(),
            jadwal = jadwalStandar,
            status = AttendanceLogic.StatusAbsensi(sudahMasuk = true, sudahPulang = true)
        )
        val vm = vm(deteksiSukses(), repo, onSiteTestingSelesai = true, jam = LocalTime.of(15, 0))

        vm.prosesFrame(ByteArray(4))

        assertTrue(repo.disimpan.isEmpty())
        assertEquals(StatusHasil.DITOLAK_SUDAH_ABSEN, vm.uiState.value.hasilTerakhir?.status)
    }

    @Test
    fun `pulang cepat dengan dispensasi - status_kehadiran_otomatis = kategori dispensasi`() = runTest {
        val repo = FakeRepo(
            match = cocok(),
            jadwal = jadwalStandar,
            status = AttendanceLogic.StatusAbsensi(sudahMasuk = true, sudahPulang = false),
            dispensasi = DispensasiCache(
                siswa_id = 7, tanggal = "2026-09-02", jenis = "PULANG_CEPAT",
                kategori = "SAKIT", alasan = "demam"
            )
        )
        val vm = vm(deteksiSukses(), repo, onSiteTestingSelesai = true, jam = LocalTime.of(13, 0))

        vm.prosesFrame(ByteArray(4))

        assertEquals(1, repo.disimpan.size)
        assertEquals("SAKIT", repo.disimpan[0].status)
        assertEquals("demam", repo.disimpan[0].catatan)
        assertEquals(HasilAbsen.BERHASIL_PULANG_CEPAT, repo.disimpan[0].hasil)
    }

    @Test
    fun `jadwal belum tersedia - tidak menyimpan`() = runTest {
        val repo = FakeRepo(match = cocok(), jadwal = null)
        val vm = vm(deteksiSukses(), repo, onSiteTestingSelesai = true)

        vm.prosesFrame(ByteArray(4))

        assertTrue(repo.disimpan.isEmpty())
        assertEquals(StatusHasil.DITOLAK_BELUM_WAKTUNYA, vm.uiState.value.hasilTerakhir?.status)
    }

    // --- helpers ---

    private fun cocok() = SiswaCocok(
        ditemukan = true, siswaId = 7, nis = "23200", nama = "Budi", kelas = "XI-E", jarak = 0.1f
    )

    private class FakeFaceEngine(private val hasil: HasilDeteksiWajah) : FaceEngine {
        override suspend fun loadModels(livenessModelPath: String, embeddingModelPath: String) {}
        override suspend fun extractEmbedding(bitmapBytes: ByteArray): FloatArray? = hasil.embedding
        override suspend fun detectLiveness(bitmapBytes: ByteArray): LivenessResult =
            LivenessResult(hasil.livenessScore, hasil.lolosLiveness, hasil.livenessScore)
        override suspend fun prosesFrame(frameBytes: ByteArray): HasilDeteksiWajah = hasil
    }

    data class Disimpan(val siswaId: Int, val hasil: HasilAbsen, val status: String, val catatan: String?)

    private class FakeRepo(
        private val match: SiswaCocok,
        private val jadwal: AttendanceLogic.JadwalEfektif?,
        private val status: AttendanceLogic.StatusAbsensi = AttendanceLogic.StatusAbsensi(false, false),
        private val dispensasi: DispensasiCache? = null,
        private val simpanBerhasil: Boolean = true,
    ) : AbsensiRepository {
        val disimpan = mutableListOf<Disimpan>()

        override suspend fun cariSiswaCocok(embedding: FloatArray, ambangJarak: Float): SiswaCocok = match
        override suspend fun jadwalEfektif(kelas: String, tanggal: String): AttendanceLogic.JadwalEfektif? = jadwal
        override suspend fun statusHariIni(siswaId: Int, tanggal: String): AttendanceLogic.StatusAbsensi = status
        override suspend fun dispensasiAktif(siswaId: Int, tanggal: String): DispensasiCache? = dispensasi
        override suspend fun simpanAbsensi(
            siswaId: Int, hasil: HasilAbsen, statusKehadiranOtomatis: String, catatan: String?
        ): Boolean {
            disimpan.add(Disimpan(siswaId, hasil, statusKehadiranOtomatis, catatan))
            return simpanBerhasil
        }
    }
}
