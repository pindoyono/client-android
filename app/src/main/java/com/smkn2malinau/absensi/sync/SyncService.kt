package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.remote.*
import com.smkn2malinau.absensi.location.LocationChecker
import retrofit2.HttpException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface SyncRepository {
    suspend fun getUnsyncedRecords(): List<AbsensiLokal>
    suspend fun updateAbsensi(absensi: AbsensiLokal)
    suspend fun insertSiswa(siswa: SiswaCache)
    suspend fun deleteSiswa(siswaId: Int)
    suspend fun insertEmbedding(embedding: EmbeddingCache)
    suspend fun deleteEmbedding(siswaId: Int)
    suspend fun hapusEnrollLokalTertimpa()
    suspend fun insertDispensasi(dispensasi: DispensasiCache)
    /** Ganti SELURUH cache jadwal dengan set baru (setara `replace_jadwal_cache` Windows). */
    suspend fun gantiJadwalCache(jadwal: List<JadwalCache>)
    /** Kelas unik dari cache siswa lokal — untuk menarik jadwal efektif per kelas. */
    suspend fun daftarKelas(): List<String>
    suspend fun getUnsyncedOverrides(): List<JadwalOverrideLokal>
    suspend fun updateOverrideLokal(override: JadwalOverrideLokal)
    suspend fun insertSyncEvent(log: SyncEventLog)
    suspend fun insertLiveness(log: LivenessLog)
    /** Seed akun login offline dari `GET /auth/roster` (PRD R-P1-2). */
    suspend fun seedAkunRoster(guru: List<RosterItemDto>)
    /**
     * Seed cache siswa dari roster lengkap `GET /siswa` — termasuk siswa yang
     * BELUM enroll wajah (yang tak dikirim `GET /embeddings/sync`). Membuang
     * baris versi-server yang tak lagi ada di roster & belum pernah enroll.
     */
    suspend fun seedSiswaRoster(siswa: List<SiswaRosterDto>)
    /** Umur cache lokal untuk `POST /device/{id}/health`. */
    suspend fun kesehatanCache(): KesehatanCache
}

data class KesehatanCache(
    val jadwalJamLalu: Double? = null,
    val wajahHariLalu: Int? = null,
    val pendingKirim: Int = 0,
)

/**
 * Satu siklus sinkronisasi client ⇆ server — kontraknya HARUS sama dengan
 * `client-windows/app/sync/service.py` (server yang sama untuk kedua client).
 *
 * Urutan: push absensi → tarik embedding → tarik jadwal (umum + per kelas) →
 * tarik dispensasi → push override lokal → lapor kesehatan device.
 * Hanya kegagalan push/tarik absensi & embedding yang menggagalkan siklus;
 * jadwal/dispensasi/override/health bersifat best-effort.
 */
class SyncService(
    private val repo: SyncRepository,
    private val api: ApiService,
    private val deviceId: String,
    /** Geofencing (opt-in per device) — lihat step 7b di runSyncCycle(). */
    private val locationChecker: LocationChecker = LocationChecker.TidakTersedia,
    /** Simpan hasil cek lokasi supaya KioskViewModel bisa membaca & memblokir layar bila perlu. */
    private val simpanStatusLokasi: (valid: Boolean, alasan: String, jarakMeter: Double?, dikonfigurasi: Boolean) -> Unit = { _, _, _, _ -> },
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    suspend fun runSyncCycle(): SyncResult {
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var duplicateCount = 0
        var failCount = 0
        var batchCount = 0

        try {
            // --- 1. Push absensi belum sync ---
            val unsynced = repo.getUnsyncedRecords()
            if (unsynced.isNotEmpty()) {
                batchCount++
                val dtoList = unsynced.map {
                    AbsensiRecordDto(
                        recordId = it.record_id,
                        siswaId = it.siswa_id,
                        tanggal = it.tanggal,
                        type = it.type,
                        jamAktual = gabungJamAktual(it.tanggal, it.jam_aktual),
                        statusKehadiranOtomatis = it.status_kehadiran_otomatis,
                        catatan = it.catatan.ifBlank { null },
                        deviceId = it.device_id
                    )
                }
                val response = api.syncAbsensi(SyncAbsensiRequest(dtoList))
                val statusById = response.hasil.associate { it.recordId.lowercase() to it.status }
                for (record in unsynced) {
                    when (statusById[record.record_id.lowercase()]) {
                        "disimpan" -> {
                            repo.updateAbsensi(
                                record.copy(synced = 1, sync_status = "ok", percobaan_sync = record.percobaan_sync + 1)
                            )
                            successCount++
                        }
                        "duplikat_diabaikan" -> {
                            repo.updateAbsensi(
                                record.copy(synced = 1, sync_status = "duplikat", percobaan_sync = record.percobaan_sync + 1)
                            )
                            duplicateCount++
                        }
                        else -> {
                            repo.updateAbsensi(
                                record.copy(synced = 0, sync_status = "gagal", percobaan_sync = record.percobaan_sync + 1)
                            )
                            failCount++
                        }
                    }
                }
            }

            // --- 2. Tarik embedding wajah siswa ---
            val nowIso = LocalDateTime.now().format(fmt)
            val embeddingResponse = api.getEmbeddings(null)
            var adaSiswaServer = false
            for (dto in embeddingResponse.data) {
                if (!dto.aktif) {
                    repo.deleteSiswa(dto.siswaId)
                    repo.deleteEmbedding(dto.siswaId)
                    continue
                }
                adaSiswaServer = true
                repo.insertSiswa(
                    SiswaCache(siswa_id = dto.siswaId, nis = dto.nis, nama = dto.nama, kelas = dto.kelas)
                )
                val bytes = dto.embeddingHex?.takeIf { it.isNotBlank() }?.let(::hexKeBytes)
                if (bytes != null) {
                    repo.insertEmbedding(
                        EmbeddingCache(
                            siswa_id = dto.siswaId,
                            embedding_encrypted = bytes,
                            model_version = dto.modelVersion,
                            diperbarui_pada = nowIso
                        )
                    )
                }
            }
            // Enroll lokal (id negatif) yang NIS-nya kini ada di server → hapus,
            // supaya matching tidak dobel dengan baris versi server.
            if (adaSiswaServer) repo.hapusEnrollLokalTertimpa()

            // --- 2b. Tarik roster siswa LENGKAP (termasuk yang belum enroll) ---
            // /embeddings/sync hanya kirim siswa ber-embedding; roster ini yang
            // mengisi layar "Data Siswa" & pilihan Enrollment dengan semua siswa.
            try {
                repo.seedSiswaRoster(api.getSiswaRoster(null, null))
            } catch (e: Exception) {
                // server lama tak izinkan GET /siswa device-auth — abaikan.
            }

            // --- 3. Tarik jadwal efektif: umum (kelas NULL) + tiap kelas ---
            try {
                val hariIni = LocalDate.now()
                val hariNama = hariIndonesia(hariIni.dayOfWeek)
                val targetKelas = buildList {
                    add(null) // jadwal umum — server balas jadwal kelas NULL, disimpan sbg kelas ""
                    addAll(repo.daftarKelas())
                }
                val entries = mutableListOf<JadwalCache>()
                for (kelas in targetKelas) {
                    try {
                        val jd = api.getJadwalEfektif(kelas)
                        val masuk = jd.jamMasuk?.takeIf { it.isNotBlank() }
                        val pulang = jd.jamPulang?.takeIf { it.isNotBlank() }
                        if (masuk != null && pulang != null) {
                            entries.add(
                                JadwalCache(
                                    kelas = kelas ?: "",
                                    tanggal = hariIni.toString(),
                                    hari = hariNama,
                                    jam_masuk = masuk,
                                    jam_pulang = pulang,
                                    sumber = jd.sumber.ifBlank { "standar" },
                                    ditarik_pada = nowIso
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // 404 = jadwal kelas ini belum diatur; lanjut kelas berikutnya.
                    }
                }
                // Ganti seluruh cache HANYA bila dapat ≥1 jadwal — supaya kegagalan
                // total (mis. semua 404) tidak mengosongkan cache lama. Ini juga
                // membuang baris jadwal hari lalu (penyebab header tidak berubah).
                if (entries.isNotEmpty()) repo.gantiJadwalCache(entries)
            } catch (e: Exception) {
                // jadwal keseluruhan opsional — jangan gagalkan siklus.
            }

            // --- 4. Tarik dispensasi aktif hari ini ---
            try {
                val dispensasiList = api.getDispensasiAktif(LocalDate.now().toString())
                for (dto in dispensasiList) {
                    repo.insertDispensasi(
                        DispensasiCache(
                            siswa_id = dto.siswaId,
                            tanggal = dto.tanggal,
                            jenis = dto.jenis,
                            kategori = dto.kategori,
                            alasan = dto.alasan ?: ""
                        )
                    )
                }
            } catch (e: Exception) {
                // dispensasi opsional — jangan gagalkan siklus.
            }

            // --- 5. Push override jadwal lokal (Opsi C) ---
            for (override in repo.getUnsyncedOverrides()) {
                if (override.status_push == "ditolak") continue
                try {
                    api.pushOverride(
                        PushOverrideRequest(
                            tanggal = override.tanggal,
                            kelas = override.kelas,
                            jamMasuk = override.jam_masuk,
                            jamPulang = override.jam_pulang,
                            alasan = override.alasan,
                            clientId = override.id
                        )
                    )
                    repo.updateOverrideLokal(override.copy(terkirim = 1, status_push = "ok"))
                } catch (e: HttpException) {
                    if (e.code() in 400..499) {
                        // server menolak (400/403/404) — tandai supaya tidak di-retry tiap siklus.
                        repo.updateOverrideLokal(
                            override.copy(terkirim = 1, status_push = "ditolak", pesan_push = "HTTP ${e.code()}")
                        )
                    }
                    // 5xx → biarkan, coba lagi siklus berikutnya.
                } catch (e: Exception) {
                    // koneksi bermasalah — coba lagi siklus berikutnya.
                }
            }

            // --- 6. Seed akun login offline dari roster (PRD R-P1-2) ---
            try {
                val roster = api.getRoster()
                if (roster.guru.isNotEmpty()) repo.seedAkunRoster(roster.guru)
            } catch (e: Exception) {
                // server versi lama tak punya /auth/roster — abaikan.
            }

            // --- 7. Lapor kesehatan device (best-effort) ---
            try {
                val k = repo.kesehatanCache()
                api.reportHealth(
                    deviceId,
                    HealthReportRequest(
                        jadwalJamLalu = k.jadwalJamLalu,
                        dispensasiJamLalu = k.jadwalJamLalu, // dispensasi cache tak bertimestamp — pakai jadwal sbg proksi
                        embeddingHariLalu = k.wajahHariLalu,
                        pendingKirim = k.pendingKirim,
                        appVersion = com.smkn2malinau.absensi.BuildConfig.VERSION_NAME,
                    )
                )
            } catch (e: Exception) {
            }

            // --- 7b. Cek geofencing (best-effort, opt-in per device) ---
            try {
                val lokasi = locationChecker.ambilLokasiSaatIni()
                val hasil = api.cekLokasi(
                    deviceId,
                    LokasiCekRequest(
                        tersedia = lokasi.tersedia,
                        lat = lokasi.lat,
                        lng = lokasi.lng,
                        akurasiMeter = lokasi.akurasiMeter,
                        mock = lokasi.mock,
                    )
                )
                simpanStatusLokasi(hasil.valid, hasil.alasan, hasil.jarakMeter, hasil.dikonfigurasi)
            } catch (e: Exception) {
                // server lama tak punya endpoint ini, atau jaringan bermasalah —
                // biarkan status lokasi lama (offline-first: jangan blokir kiosk
                // hanya karena tidak bisa terhubung ke server).
            }

            val nowStr = LocalDateTime.now().format(fmt)
            repo.insertSyncEvent(
                SyncEventLog(
                    timestamp = nowStr,
                    duration_ms = System.currentTimeMillis() - startTime,
                    status = "success",
                    batch_count = batchCount,
                    success_count = successCount,
                    duplicate_count = duplicateCount,
                    fail_count = failCount,
                    error_message = null,
                    device_id = deviceId,
                    created_at = nowStr
                )
            )

            return SyncResult.Success(batchCount, successCount, duplicateCount, failCount)
        } catch (e: Exception) {
            val errorMessage = ringkasError(e)
            val nowStr = LocalDateTime.now().format(fmt)
            repo.insertSyncEvent(
                SyncEventLog(
                    timestamp = nowStr,
                    duration_ms = System.currentTimeMillis() - startTime,
                    status = "failed",
                    batch_count = batchCount,
                    success_count = successCount,
                    duplicate_count = duplicateCount,
                    fail_count = failCount,
                    error_message = errorMessage,
                    device_id = deviceId,
                    created_at = nowStr
                )
            )
            return SyncResult.Failure(errorMessage)
        }
    }

    /** `"2026-09-04"` + `"07:31:05"` → `"2026-09-04T07:31:05"` (server parse `jam_aktual` sbg datetime). */
    private fun gabungJamAktual(tanggal: String, jam: String): String {
        val j = jam.trim()
        if (j.contains('T')) return j
        val jamLengkap = if (j.count { it == ':' } < 2) "$j:00" else j
        return "${tanggal}T$jamLengkap"
    }

    private fun hexKeBytes(hex: String): ByteArray? {
        val bersih = hex.trim().removePrefix("0x")
        if (bersih.length % 2 != 0) return null
        return try {
            ByteArray(bersih.length / 2) { i ->
                val hi = Character.digit(bersih[i * 2], 16)
                val lo = Character.digit(bersih[i * 2 + 1], 16)
                if (hi < 0 || lo < 0) return null
                ((hi shl 4) or lo).toByte()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun hariIndonesia(hari: DayOfWeek): String = when (hari) {
        DayOfWeek.MONDAY -> "SENIN"
        DayOfWeek.TUESDAY -> "SELASA"
        DayOfWeek.WEDNESDAY -> "RABU"
        DayOfWeek.THURSDAY -> "KAMIS"
        DayOfWeek.FRIDAY -> "JUMAT"
        DayOfWeek.SATURDAY -> "SABTU"
        DayOfWeek.SUNDAY -> "MINGGU"
    }

    private fun ringkasError(e: Exception): String = when (e) {
        is HttpException -> "HTTP ${e.code()} ${e.message()}".trim()
        else -> e.message ?: e.javaClass.simpleName
    }
}

sealed class SyncResult {
    data class Success(
        val batchCount: Int,
        val successCount: Int,
        val duplicateCount: Int,
        val failCount: Int
    ) : SyncResult()

    data class Failure(val error: String?) : SyncResult()
}
