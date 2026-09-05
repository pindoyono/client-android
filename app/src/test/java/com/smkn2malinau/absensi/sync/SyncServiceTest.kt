package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.remote.*
import com.smkn2malinau.absensi.location.HasilLokasi
import com.smkn2malinau.absensi.location.LocationChecker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test SyncService — kontrak HARUS sama dengan server (`absensi-server-fase1`)
 * dan `client-windows/app/sync/service.py`. Skenario: hasil per-record,
 * embedding hex, jadwal per kelas, push override, gagal total.
 */
class SyncServiceTest {

    @Test
    fun `hasil per-record dari server ditandai berbeda`() = runTest {
        val repo = FakeSyncRepo(unsynced = listOf(absensi("r1"), absensi("r2"), absensi("r3")))
        val api = FakeApi(
            syncResponse = SyncAbsensiResponse(
                total = 3, disimpan = 1, duplikat = 1, gagal = 1,
                hasil = listOf(
                    SyncResultItemDto("r1", "disimpan"),
                    SyncResultItemDto("r2", "duplikat_diabaikan"),
                    SyncResultItemDto("r3", "gagal", "constraint"),
                )
            )
        )

        val result = SyncService(repo, api, "device_01").runSyncCycle()

        assertTrue(result is SyncResult.Success)
        assertEquals("ok", repo.updated["r1"]?.sync_status)
        assertEquals(1, repo.updated["r1"]?.synced)
        assertEquals("duplikat", repo.updated["r2"]?.sync_status)
        assertEquals(1, repo.updated["r2"]?.synced)
        assertEquals("gagal", repo.updated["r3"]?.sync_status)
        assertEquals(0, repo.updated["r3"]?.synced)
    }

    @Test
    fun `jam_aktual dikirim sebagai datetime penuh`() = runTest {
        val repo = FakeSyncRepo(unsynced = listOf(absensi("r1")))
        val api = FakeApi(syncResponse = SyncAbsensiResponse(hasil = listOf(SyncResultItemDto("r1", "disimpan"))))

        SyncService(repo, api, "d").runSyncCycle()

        assertEquals("2026-09-02T06:30:00", api.lastSyncRequest?.records?.first()?.jamAktual)
    }

    @Test
    fun `embedding hex dari server disimpan ke embedding cache`() = runTest {
        val raw = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 15, 16, -1)
        val repo = FakeSyncRepo()
        val api = FakeApi(
            embeddingResponse = EmbeddingSyncResponse(
                jumlah = 1,
                data = listOf(
                    SiswaEmbeddingDto(
                        siswaId = 7, nis = "23200", nama = "Budi", kelas = "XI-E",
                        aktif = true, embeddingHex = raw.joinToString("") { "%02x".format(it) },
                        modelVersion = "v1"
                    )
                )
            )
        )

        SyncService(repo, api, "device_01").runSyncCycle()

        assertEquals(1, repo.embeddings.size)
        assertEquals(7, repo.embeddings[0].siswa_id)
        assertTrue(raw.contentEquals(repo.embeddings[0].embedding_encrypted))
    }

    @Test
    fun `siswa non-aktif menghapus siswa dan embedding`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(
            embeddingResponse = EmbeddingSyncResponse(
                jumlah = 1,
                data = listOf(SiswaEmbeddingDto(9, "1", "X", "Y", aktif = false))
            )
        )

        SyncService(repo, api, "d").runSyncCycle()

        assertEquals(listOf(9), repo.deletedSiswa)
        assertEquals(listOf(9), repo.deletedEmbedding)
    }

    @Test
    fun `jadwal efektif ditarik untuk jadwal umum dan tiap kelas`() = runTest {
        val repo = FakeSyncRepo(kelas = listOf("XI-E"))
        val api = FakeApi(jadwalResponse = JadwalEfektifDto("standar", "07:00:00", "14:00:00"))

        SyncService(repo, api, "d").runSyncCycle()

        // 1 panggilan untuk kelas null (umum → disimpan sbg "") + 1 untuk "XI-E"
        assertEquals(listOf(null, "XI-E"), api.jadwalKelasDiminta)
        assertEquals(setOf("", "XI-E"), repo.jadwal.map { it.kelas }.toSet())
        assertEquals("07:00:00", repo.jadwal.first { it.kelas == "" }.jam_masuk)
    }

    @Test
    fun `jadwal 404 satu kelas tidak menggagalkan siklus`() = runTest {
        val repo = FakeSyncRepo(kelas = listOf("XI-E"))
        val api = FakeApi(jadwalResponse = JadwalEfektifDto("tidak_ada_sekolah", null, null))

        val result = SyncService(repo, api, "d").runSyncCycle()

        assertTrue(result is SyncResult.Success)
        assertTrue(repo.jadwal.isEmpty())
    }

    @Test
    fun `dispensasi aktif ditarik dengan tanggal dan disimpan`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(
            dispensasiResponse = listOf(
                DispensasiDto(id = 1, siswaId = 7, tanggal = "2026-09-02", jenis = "PULANG_CEPAT", kategori = "SAKIT", alasan = "demam")
            )
        )

        SyncService(repo, api, "d").runSyncCycle()

        assertTrue(api.dispensasiTanggalDiminta?.isNotBlank() == true)
        assertEquals(1, repo.dispensasi.size)
        assertEquals("SAKIT", repo.dispensasi[0].kategori)
    }

    @Test
    fun `push override diterima server - ditandai ok`() = runTest {
        val repo = FakeSyncRepo(
            unsyncedOverrides = listOf(
                JadwalOverrideLokal(
                    id = "o1", tanggal = "2026-09-02", kelas = "XI-E",
                    jam_masuk = "08:00:00", jam_pulang = "12:00:00", alasan = "rapat",
                    dibuat_pada = "2026-09-02T06:00:00"
                )
            )
        )
        val api = FakeApi(pushResponse = PushOverrideResponse(id = 5, sumber = "device", clientId = "o1"))

        SyncService(repo, api, "d").runSyncCycle()

        assertEquals("o1", api.lastOverrideRequest?.clientId)
        assertEquals(1, repo.updatedOverride["o1"]?.terkirim)
        assertEquals("ok", repo.updatedOverride["o1"]?.status_push)
    }

    @Test
    fun `roster dari server di-seed ke akun lokal`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(rosterResponse = RosterResponse(guru = listOf(
            RosterItemDto("budi@s.sch.id", "Budi", "admin", true),
            RosterItemDto("sri@s.sch.id", "Sri", "guru_piket", true),
        )))

        SyncService(repo, api, "d").runSyncCycle()

        assertEquals(2, repo.rosterDiseed.size)
        assertEquals("admin", repo.rosterDiseed.first { it.email == "budi@s.sch.id" }.role)
    }

    @Test
    fun `roster siswa lengkap di-seed ke cache siswa`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(siswaRosterResponse = listOf(
            SiswaRosterDto(id = 1, nis = "23001", nama = "Ani", kelas = "XI-E", enrolled = true),
            SiswaRosterDto(id = 2, nis = "23002", nama = "Budi", kelas = "XI-E", enrolled = false),
        ))

        SyncService(repo, api, "d").runSyncCycle()

        assertEquals(2, repo.siswaRosterDiseed.size)
        assertEquals(setOf("23001", "23002"), repo.siswaRosterDiseed.map { it.nis }.toSet())
    }

    @Test
    fun `hasil cek lokasi disimpan lewat callback`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(lokasiCekResponse = LokasiCekResponse(valid = false, alasan = "di luar radius", jarakMeter = 512.3))
        var statusTersimpan: Triple<Boolean, String, Double?>? = null

        SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4, lng = 116.4)),
            simpanStatusLokasi = { valid, alasan, jarak -> statusTersimpan = Triple(valid, alasan, jarak) },
        ).runSyncCycle()

        assertEquals(Triple(false, "di luar radius", 512.3), statusTersimpan)
        assertEquals(true, api.lastLokasiCekRequest?.tersedia)
        assertEquals(-3.4, api.lastLokasiCekRequest?.lat)
    }

    @Test
    fun `cek lokasi gagal - siklus tetap sukses, status lama tidak ditimpa`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(throwOnLokasiCek = true)
        var dipanggil = false

        val result = SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = false)),
            simpanStatusLokasi = { _, _, _ -> dipanggil = true },
        ).runSyncCycle()

        assertTrue(result is SyncResult.Success)
        assertTrue(!dipanggil) // best-effort: gagal panggil server -> callback tidak dipanggil sama sekali
    }

    @Test
    fun `sync gagal total - insertSyncEvent status failed dan SyncResult Failure`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(throwOnEmbeddings = true)

        val result = SyncService(repo, api, "d").runSyncCycle()

        assertTrue(result is SyncResult.Failure)
        assertEquals(1, repo.syncEvents.size)
        assertEquals("failed", repo.syncEvents.last().status)
    }

    // --- fakes ---

    private fun absensi(id: String) = AbsensiLokal(
        record_id = id, siswa_id = 1, tanggal = "2026-09-02", type = "MASUK",
        jam_aktual = "06:30:00", status_kehadiran_otomatis = "NORMAL", catatan = "",
        device_id = "device_01", dibuat_pada = "2026-09-02T06:30:00"
    )

    private class FakeApi(
        private val syncResponse: SyncAbsensiResponse = SyncAbsensiResponse(),
        private val embeddingResponse: EmbeddingSyncResponse = EmbeddingSyncResponse(),
        private val jadwalResponse: JadwalEfektifDto = JadwalEfektifDto(),
        private val dispensasiResponse: List<DispensasiDto> = emptyList(),
        private val pushResponse: PushOverrideResponse = PushOverrideResponse(),
        private val rosterResponse: RosterResponse = RosterResponse(),
        private val siswaRosterResponse: List<SiswaRosterDto> = emptyList(),
        private val throwOnEmbeddings: Boolean = false,
        private val lokasiCekResponse: LokasiCekResponse = LokasiCekResponse(valid = true, alasan = "lokasi belum diatur"),
        private val throwOnLokasiCek: Boolean = false,
    ) : ApiService {
        var lastSyncRequest: SyncAbsensiRequest? = null
        var lastOverrideRequest: PushOverrideRequest? = null
        val jadwalKelasDiminta = mutableListOf<String?>()
        var dispensasiTanggalDiminta: String? = null

        override suspend fun loginGoogle(request: GoogleLoginRequest) = error("n/a")
        override suspend fun registerDevice(bearer: String, request: DeviceRegisterRequest) = error("n/a")
        override suspend fun syncAbsensi(request: SyncAbsensiRequest): SyncAbsensiResponse {
            lastSyncRequest = request
            return syncResponse
        }
        override suspend fun getEmbeddings(diperbaruiSejak: String?): EmbeddingSyncResponse {
            if (throwOnEmbeddings) throw RuntimeException("boom")
            return embeddingResponse
        }
        override suspend fun getJadwalEfektif(kelas: String?): JadwalEfektifDto {
            jadwalKelasDiminta.add(kelas)
            return jadwalResponse
        }
        override suspend fun getDispensasiAktif(tanggal: String): List<DispensasiDto> {
            dispensasiTanggalDiminta = tanggal
            return dispensasiResponse
        }
        override suspend fun pushOverride(request: PushOverrideRequest): PushOverrideResponse {
            lastOverrideRequest = request
            return pushResponse
        }
        override suspend fun reportHealth(deviceId: String, request: HealthReportRequest) = HealthReportResponse("ok")
        override suspend fun getRoster() = rosterResponse
        override suspend fun getSiswaRoster(kelas: String?, enrolled: Boolean?) = siswaRosterResponse
        var lastLokasiCekRequest: LokasiCekRequest? = null
        override suspend fun cekLokasi(deviceId: String, request: LokasiCekRequest): LokasiCekResponse {
            if (throwOnLokasiCek) throw RuntimeException("boom")
            lastLokasiCekRequest = request
            return lokasiCekResponse
        }
    }

    private class FakeLocationChecker(private val hasil: HasilLokasi) : LocationChecker {
        override suspend fun ambilLokasiSaatIni(): HasilLokasi = hasil
    }

    private class FakeSyncRepo(
        private val unsynced: List<AbsensiLokal> = emptyList(),
        private val unsyncedOverrides: List<JadwalOverrideLokal> = emptyList(),
        private val kelas: List<String> = emptyList(),
    ) : SyncRepository {
        val updated = mutableMapOf<String, AbsensiLokal>()
        val updatedOverride = mutableMapOf<String, JadwalOverrideLokal>()
        val embeddings = mutableListOf<EmbeddingCache>()
        val jadwal = mutableListOf<JadwalCache>()
        val dispensasi = mutableListOf<DispensasiCache>()
        val deletedSiswa = mutableListOf<Int>()
        val deletedEmbedding = mutableListOf<Int>()
        val syncEvents = mutableListOf<SyncEventLog>()
        var enrollLokalDibersihkan = 0

        override suspend fun getUnsyncedRecords() = unsynced
        override suspend fun updateAbsensi(absensi: AbsensiLokal) { updated[absensi.record_id] = absensi }
        override suspend fun insertSiswa(siswa: SiswaCache) {}
        override suspend fun deleteSiswa(siswaId: Int) { deletedSiswa.add(siswaId) }
        override suspend fun insertEmbedding(embedding: EmbeddingCache) { embeddings.add(embedding) }
        override suspend fun deleteEmbedding(siswaId: Int) { deletedEmbedding.add(siswaId) }
        override suspend fun hapusEnrollLokalTertimpa() { enrollLokalDibersihkan++ }
        override suspend fun insertDispensasi(dispensasi: DispensasiCache) { this.dispensasi.add(dispensasi) }
        override suspend fun gantiJadwalCache(jadwal: List<JadwalCache>) { this.jadwal.clear(); this.jadwal.addAll(jadwal) }
        override suspend fun daftarKelas() = kelas
        override suspend fun getUnsyncedOverrides() = unsyncedOverrides
        override suspend fun updateOverrideLokal(override: JadwalOverrideLokal) { updatedOverride[override.id] = override }
        override suspend fun insertSyncEvent(log: SyncEventLog) { syncEvents.add(log) }
        override suspend fun insertLiveness(log: LivenessLog) {}
        val rosterDiseed = mutableListOf<com.smkn2malinau.absensi.data.remote.RosterItemDto>()
        override suspend fun seedAkunRoster(guru: List<com.smkn2malinau.absensi.data.remote.RosterItemDto>) { rosterDiseed.addAll(guru) }
        val siswaRosterDiseed = mutableListOf<com.smkn2malinau.absensi.data.remote.SiswaRosterDto>()
        override suspend fun seedSiswaRoster(siswa: List<com.smkn2malinau.absensi.data.remote.SiswaRosterDto>) { siswaRosterDiseed.addAll(siswa) }
        override suspend fun kesehatanCache() = KesehatanCache()
    }
}
