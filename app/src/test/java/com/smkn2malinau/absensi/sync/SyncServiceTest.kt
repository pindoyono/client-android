package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.remote.*
import com.smkn2malinau.absensi.location.HasilLokasi
import com.smkn2malinau.absensi.location.KonfigLokasi
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
    fun `flag lokasi_mock record lokal ikut terkirim di DTO sync`() = runTest {
        val repo = FakeSyncRepo(unsynced = listOf(absensi("r1").copy(lokasi_mock = 1), absensi("r2")))
        val api = FakeApi(syncResponse = SyncAbsensiResponse(hasil = listOf(
            SyncResultItemDto("r1", "disimpan"), SyncResultItemDto("r2", "disimpan"),
        )))

        SyncService(repo, api, "d").runSyncCycle()

        val dtoById = api.lastSyncRequest!!.records.associateBy { it.recordId }
        assertEquals(true, dtoById["r1"]?.lokasiMock)
        assertEquals(false, dtoById["r2"]?.lokasiMock)
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
        val api = FakeApi(lokasiCekResponse = LokasiCekResponse(
            valid = false, alasan = "di luar radius", jarakMeter = 512.3, dikonfigurasi = true,
        ))
        data class Tersimpan(val valid: Boolean, val alasan: String, val jarak: Double?, val dikonfigurasi: Boolean)
        var statusTersimpan: Tersimpan? = null

        SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4, lng = 116.4)),
            simpanStatusLokasi = { valid, alasan, jarak, dikonfigurasi ->
                statusTersimpan = Tersimpan(valid, alasan, jarak, dikonfigurasi)
            },
        ).runSyncCycle()

        assertEquals(Tersimpan(false, "di luar radius", 512.3, true), statusTersimpan)
        assertEquals(true, api.lastLokasiCekRequest?.tersedia)
        assertEquals(-3.4, api.lastLokasiCekRequest?.lat)
    }

    @Test
    fun `cek lokasi online gagal tapi konfigurasi ter-cache - fallback validasi lokal jalan`() = runTest {
        val repo = FakeSyncRepo()
        // getLokasiKonfig gagal juga (offline sungguhan) -> pakai cache dari sync SEBELUMNYA.
        val api = FakeApi(throwOnLokasiCek = true, throwOnLokasiKonfig = true)
        var tersimpan: BooleanArray? = null
        var alasanTersimpan: String? = null
        var jarakTersimpan: Double? = null

        // Titik acuan & radius yang "sudah pernah" di-cache sebelum device offline.
        val titikAcuan = KonfigLokasi(lat = -3.4295, lng = 116.4396, radiusMeter = 100)

        val result = SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4295, lng = 116.4396)),
            simpanStatusLokasi = { valid, alasan, jarak, _ ->
                tersimpan = booleanArrayOf(valid)
                alasanTersimpan = alasan
                jarakTersimpan = jarak
            },
            ambilKonfigLokasi = { titikAcuan },
        ).runSyncCycle()

        assertTrue(result is SyncResult.Success)
        assertEquals(true, tersimpan?.get(0)) // tepat di titik acuan -> dalam radius
        assertTrue(alasanTersimpan!!.contains("[offline]"))
        assertTrue(jarakTersimpan!! < 1.0)
    }

    @Test
    fun `cek lokasi online gagal, di luar radius - fallback lokal menolak`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(throwOnLokasiCek = true, throwOnLokasiKonfig = true)
        var validTersimpan: Boolean? = null
        val titikAcuan = KonfigLokasi(lat = -3.4295, lng = 116.4396, radiusMeter = 50)

        SyncService(
            repo, api, "d",
            // ~1.1km dari titik acuan (0.01 derajat lat)
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4195, lng = 116.4396)),
            simpanStatusLokasi = { valid, _, _, _ -> validTersimpan = valid },
            ambilKonfigLokasi = { titikAcuan },
        ).runSyncCycle()

        assertEquals(false, validTersimpan)
    }

    @Test
    fun `cek lokasi online gagal, GPS palsu - fallback lokal tetap menolak`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(throwOnLokasiCek = true, throwOnLokasiKonfig = true)
        var validTersimpan: Boolean? = null
        var alasanTersimpan: String? = null

        SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4295, lng = 116.4396, mock = true)),
            simpanStatusLokasi = { valid, alasan, _, _ -> validTersimpan = valid; alasanTersimpan = alasan },
            ambilKonfigLokasi = { KonfigLokasi(-3.4295, 116.4396, 100) },
        ).runSyncCycle()

        assertEquals(false, validTersimpan)
        assertTrue(alasanTersimpan!!.contains("palsu"))
    }

    @Test
    fun `cek lokasi online bilang valid tapi GPS palsu - client tetap menolak (fail-closed)`() = runTest {
        val repo = FakeSyncRepo()
        // Server (versi lama) mengabaikan flag mock & membalas valid.
        val api = FakeApi(lokasiCekResponse = LokasiCekResponse(
            valid = true, alasan = "dalam radius", jarakMeter = 5.0, dikonfigurasi = true,
        ))
        var validTersimpan: Boolean? = null
        var alasanTersimpan: String? = null

        SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4295, lng = 116.4396, mock = true)),
            simpanStatusLokasi = { valid, alasan, _, dikonfigurasi ->
                validTersimpan = valid; alasanTersimpan = alasan
                assertTrue(dikonfigurasi)
            },
        ).runSyncCycle()

        assertEquals(false, validTersimpan)
        assertTrue(alasanTersimpan!!.contains("palsu"))
    }

    @Test
    fun `cek lokasi online gagal dan belum pernah ada konfigurasi ter-cache - fail-closed`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(throwOnLokasiCek = true, throwOnLokasiKonfig = true)
        var validTersimpan: Boolean? = null

        SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4295, lng = 116.4396)),
            simpanStatusLokasi = { valid, _, _, _ -> validTersimpan = valid },
            ambilKonfigLokasi = { KonfigLokasi(null, null, null) }, // belum pernah online sama sekali
        ).runSyncCycle()

        assertEquals(false, validTersimpan)
    }

    @Test
    fun `cek lokasi online berhasil - konfigurasi ikut di-cache untuk offline berikutnya`() = runTest {
        val repo = FakeSyncRepo()
        val api = FakeApi(
            lokasiCekResponse = LokasiCekResponse(valid = true, alasan = "dalam radius", jarakMeter = 5.0, dikonfigurasi = true),
            lokasiKonfigResponse = LokasiKonfigResponse(lokasiLat = -3.4295, lokasiLng = 116.4396, radiusMeter = 100),
        )
        var konfigTersimpan: Triple<Double?, Double?, Int?>? = null

        SyncService(
            repo, api, "d",
            locationChecker = FakeLocationChecker(HasilLokasi(tersedia = true, lat = -3.4295, lng = 116.4396)),
            simpanKonfigLokasi = { lat, lng, radius -> konfigTersimpan = Triple(lat, lng, radius) },
        ).runSyncCycle()

        assertEquals(Triple(-3.4295, 116.4396, 100), konfigTersimpan)
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
        private val lokasiKonfigResponse: LokasiKonfigResponse = LokasiKonfigResponse(),
        private val throwOnLokasiKonfig: Boolean = false,
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
        override suspend fun getLokasiKonfig(deviceId: String): LokasiKonfigResponse {
            if (throwOnLokasiKonfig) throw RuntimeException("boom")
            return lokasiKonfigResponse
        }
        override suspend fun getJadwalStandarServer(bearer: String) = emptyList<JadwalStandarDto>()
        override suspend fun getJadwalOverrideServer(bearer: String) = emptyList<JadwalOverrideServerDto>()
        override suspend fun hapusJadwalOverrideServer(bearer: String, id: Int) {}
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
        var overrideKedaluwarsaDibersihkan = 0
        override suspend fun hapusOverrideLokalKedaluwarsa() { overrideKedaluwarsaDibersihkan++ }
        override suspend fun insertSyncEvent(log: SyncEventLog) { syncEvents.add(log) }
        override suspend fun insertLiveness(log: LivenessLog) {}
        val rosterDiseed = mutableListOf<com.smkn2malinau.absensi.data.remote.RosterItemDto>()
        override suspend fun seedAkunRoster(guru: List<com.smkn2malinau.absensi.data.remote.RosterItemDto>) { rosterDiseed.addAll(guru) }
        val siswaRosterDiseed = mutableListOf<com.smkn2malinau.absensi.data.remote.SiswaRosterDto>()
        override suspend fun seedSiswaRoster(siswa: List<com.smkn2malinau.absensi.data.remote.SiswaRosterDto>) { siswaRosterDiseed.addAll(siswa) }
        override suspend fun kesehatanCache() = KesehatanCache()
    }
}
