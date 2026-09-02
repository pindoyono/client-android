package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.remote.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Test SyncService (PRD bagian 6) — skenario gagal-sebagian, push override,
 * dan embeddings/jadwal benar-benar mendarat di repo.
 */
class SyncServiceTest {

    @Test
    fun `sync gagal-sebagian - diterima duplikat dan gagal ditandai berbeda`() = runTest {
        val repo = FakeSyncRepo(
            unsynced = listOf(
                absensi("r1"), absensi("r2"), absensi("r3")
            )
        )
        val api = FakeApi(
            syncResponse = SyncAbsensiResponse(
                status = "ok",
                diterima = listOf("r1"),
                duplikat = listOf("r2"),
                gagal = listOf("r3")
            )
        )
        val service = SyncService(repo, api, "device_01")

        val result = service.runSyncCycle()

        assertTrue(result is SyncResult.Success)
        assertEquals("ok", repo.updated["r1"]?.sync_status)
        assertEquals(1, repo.updated["r1"]?.synced)
        assertEquals("duplikat", repo.updated["r2"]?.sync_status)
        assertEquals("gagal", repo.updated["r3"]?.sync_status)
        assertEquals(0, repo.updated["r3"]?.synced)
    }

    @Test
    fun `embedding base64 dari server disimpan ke embedding cache`() = runTest {
        val raw = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val repo = FakeSyncRepo()
        val api = FakeApi(
            embeddingResponse = EmbeddingSyncResponse(
                siswaList = listOf(
                    SiswaEmbeddingDto(
                        siswaId = 7, nis = "23200", nama = "Budi", kelas = "XI-E",
                        embeddingBase64 = Base64.getEncoder().encodeToString(raw),
                        modelVersion = "v1", aktif = true
                    )
                )
            )
        )
        val service = SyncService(repo, api, "device_01")

        service.runSyncCycle()

        assertEquals(1, repo.embeddings.size)
        assertEquals(7, repo.embeddings[0].siswa_id)
        assertTrue(raw.contentEquals(repo.embeddings[0].embedding_encrypted))
    }

    @Test
    fun `siswa non-aktif menghapus siswa dan embedding`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(
            embeddingResponse = EmbeddingSyncResponse(
                siswaList = listOf(
                    SiswaEmbeddingDto(9, "1", "X", "Y", null, "v1", aktif = false)
                )
            )
        )
        SyncService(repo, api, "d").runSyncCycle()

        assertEquals(listOf(9), repo.deletedSiswa)
        assertEquals(listOf(9), repo.deletedEmbedding)
    }

    @Test
    fun `jadwal efektif dari server disimpan ke jadwal cache`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(
            jadwalResponse = JadwalEfektifResponse(
                jadwalList = listOf(
                    JadwalDto("XI-E", "2026-09-02", "SELASA", "07:00", "14:00", "standar")
                )
            )
        )
        SyncService(repo, api, "d").runSyncCycle()

        assertEquals(1, repo.jadwal.size)
        assertEquals("XI-E", repo.jadwal[0].kelas)
        assertEquals("07:00", repo.jadwal[0].jam_masuk)
    }

    @Test
    fun `push override diterima server - ditandai ok`() = runTest {
        val repo = FakeSyncRepo(
            unsyncedOverrides = listOf(
                JadwalOverrideLokal(
                    id = "o1", tanggal = "2026-09-02", kelas = "XI-E",
                    jam_masuk = "08:00", jam_pulang = "12:00", alasan = "rapat",
                    dibuat_pada = "2026-09-02T06:00:00"
                )
            )
        )
        val api = FakeApi(pushResponse = PushOverrideResponse(status = "ok", pesan = null))

        SyncService(repo, api, "d").runSyncCycle()

        assertEquals(1, repo.updatedOverride["o1"]?.terkirim)
        assertEquals("ok", repo.updatedOverride["o1"]?.status_push)
    }

    @Test
    fun `sync gagal total - insertSyncEvent status failed dan SyncResult Failure`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(throwOnEmbeddings = true)

        val result = SyncService(repo, api, "d").runSyncCycle()

        assertTrue(result is SyncResult.Failure)
        assertEquals("failed", repo.syncEvents.last().status)
    }

    // --- fakes ---

    private fun absensi(id: String) = AbsensiLokal(
        record_id = id, siswa_id = 1, tanggal = "2026-09-02", type = "MASUK",
        jam_aktual = "06:30", status_kehadiran_otomatis = "NORMAL", catatan = "",
        device_id = "device_01", dibuat_pada = "2026-09-02T06:30:00"
    )

    private class FakeApi(
        private val syncResponse: SyncAbsensiResponse = SyncAbsensiResponse("ok", emptyList(), emptyList(), emptyList()),
        private val embeddingResponse: EmbeddingSyncResponse = EmbeddingSyncResponse(emptyList()),
        private val jadwalResponse: JadwalEfektifResponse = JadwalEfektifResponse(emptyList()),
        private val dispensasiResponse: DispensasiAktifResponse = DispensasiAktifResponse(emptyList()),
        private val pushResponse: PushOverrideResponse = PushOverrideResponse("ok", null),
        private val throwOnEmbeddings: Boolean = false,
    ) : ApiService {
        override suspend fun syncAbsensi(request: SyncAbsensiRequest) = syncResponse
        override suspend fun getEmbeddings(): EmbeddingSyncResponse {
            if (throwOnEmbeddings) throw RuntimeException("boom")
            return embeddingResponse
        }
        override suspend fun getJadwalEfektif() = jadwalResponse
        override suspend fun getDispensasiAktif() = dispensasiResponse
        override suspend fun pushOverride(request: PushOverrideRequest) = pushResponse
        override suspend fun reportHealth(deviceId: String, request: HealthReportRequest) = HealthReportResponse("ok")
    }

    private class FakeSyncRepo(
        private val unsynced: List<AbsensiLokal> = emptyList(),
        private val unsyncedOverrides: List<JadwalOverrideLokal> = emptyList(),
    ) : SyncRepository {
        val updated = mutableMapOf<String, AbsensiLokal>()
        val updatedOverride = mutableMapOf<String, JadwalOverrideLokal>()
        val embeddings = mutableListOf<EmbeddingCache>()
        val jadwal = mutableListOf<JadwalCache>()
        val deletedSiswa = mutableListOf<Int>()
        val deletedEmbedding = mutableListOf<Int>()
        val syncEvents = mutableListOf<SyncEventLog>()

        override suspend fun getUnsyncedRecords() = unsynced
        override suspend fun updateAbsensi(absensi: AbsensiLokal) { updated[absensi.record_id] = absensi }
        override suspend fun insertSiswa(siswa: SiswaCache) {}
        override suspend fun deleteSiswa(siswaId: Int) { deletedSiswa.add(siswaId) }
        override suspend fun insertEmbedding(embedding: EmbeddingCache) { embeddings.add(embedding) }
        override suspend fun deleteEmbedding(siswaId: Int) { deletedEmbedding.add(siswaId) }
        override suspend fun insertDispensasi(dispensasi: DispensasiCache) {}
        override suspend fun insertJadwal(jadwal: JadwalCache) { this.jadwal.add(jadwal) }
        override suspend fun getUnsyncedOverrides() = unsyncedOverrides
        override suspend fun updateOverrideLokal(override: JadwalOverrideLokal) { updatedOverride[override.id] = override }
        override suspend fun insertSyncEvent(log: SyncEventLog) { syncEvents.add(log) }
        override suspend fun insertLiveness(log: LivenessLog) {}
    }
}
