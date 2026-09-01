package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.remote.ApiService
import com.smkn2malinau.absensi.data.remote.SyncAbsensiRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Abstraksi repository untuk SyncService — supaya SyncService bisa diuji
 * tanpa Android framework (Room). PRD bagian 9.
 */
interface SyncRepository {
    suspend fun getUnsyncedRecords(): List<AbsensiLokalEntity>
    suspend fun updateAbsensi(absensi: AbsensiLokalEntity)
    suspend fun insertSiswa(siswa: SiswaCacheEntity)
    suspend fun deleteSiswa(siswaId: Long)
    suspend fun insertDispensasi(dispensasi: DispensasiCacheEntity)
    suspend fun getUnsyncedOverrides(): List<JadwalOverrideLokalEntity>
    suspend fun updateOverrideLokal(override: JadwalOverrideLokalEntity)
    suspend fun insertSyncEvent(log: SyncEventLogEntity)
    suspend fun insertLiveness(log: LivenessLogEntity)
}

/**
 * SyncService — logika murni, tidak bergantung Android framework.
 * PRD bagian 9.1–9.3.
 */
class SyncService(
    private val repo: SyncRepository,
    private val api: ApiService,
    private val deviceId: String
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    suspend fun runSyncCycle(): SyncResult {
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var duplicateCount = 0
        var failCount = 0
        var batchCount = 0
        var errorMessage: String? = null

        try {
            // 1. Push unsynced absensi records
            val unsynced = repo.getUnsyncedRecords()
            if (unsynced.isNotEmpty()) {
                batchCount++
                val dtoList = unsynced.map {
                    AbsensiRecordDto(
                        recordId = it.recordId,
                        siswaId = it.siswaId,
                        tanggal = it.tanggal,
                        type = it.type,
                        jamAktual = it.jamAktual,
                        statusKehadiranOtomatis = it.statusKehadiranOtomatis,
                        catatan = it.catatan,
                        deviceId = it.deviceId
                    )
                }
                val response = api.syncAbsensi(SyncAbsensiRequest(dtoList))
                for (record in unsynced) {
                    if (response.diterima.contains(record.recordId)) {
                        repo.updateAbsensi(
                            record.copy(synced = 1, syncStatus = "ok", percobaanSync = record.percobaanSync + 1)
                        )
                        successCount++
                    } else if (response.duplikat.contains(record.recordId)) {
                        repo.updateAbsensi(
                            record.copy(synced = 1, syncStatus = "duplikat", percobaanSync = record.percobaanSync + 1)
                        )
                        duplicateCount++
                    } else {
                        repo.updateAbsensi(
                            record.copy(synced = 0, syncStatus = "gagal", percobaanSync = record.percobaanSync + 1)
                        )
                        failCount++
                    }
                }
            }

            // 2. Pull embeddings (hapus siswa nonaktif — PRD 9.2)
            val embeddingResponse = api.getEmbeddings()
            for (dto in embeddingResponse.siswaList) {
                if (dto.aktif) {
                    repo.insertSiswa(
                        SiswaCacheEntity(
                            siswaId = dto.siswaId,
                            nis = dto.nis,
                            nama = dto.nama,
                            kelas = dto.kelas
                        )
                    )
                } else {
                    // Hapus siswa nonaktif
                    repo.deleteSiswa(dto.siswaId)
                }
            }

            // 3. Pull dispensasi
            val dispensasiResponse = api.getDispensasiAktif()
            for (dto in dispensasiResponse.dispensasiList) {
                repo.insertDispensasi(
                    DispensasiCacheEntity(
                        siswaId = dto.siswaId,
                        tanggal = dto.tanggal,
                        jenis = dto.jenis,
                        kategori = dto.kategori,
                        alasan = dto.alasan
                    )
                )
            }

            // 4. Push local overrides (PRD 9.1)
            val unsyncedOverrides = repo.getUnsyncedOverrides()
            for (override in unsyncedOverrides) {
                if (override.statusPush == "ditolak") continue // jangan retry yang ditolak permanen
                try {
                    val response = api.pushOverride(
                        com.smkn2malinau.absensi.data.remote.PushOverrideRequest(
                            clientId = override.id,
                            tanggal = override.tanggal,
                            kelas = override.kelas,
                            jamMasuk = override.jamMasuk,
                            jamPulang = override.jamPulang,
                            alasan = override.alasan
                        )
                    )
                    if (response.status == "ok") {
                        repo.updateOverrideLokal(
                            override.copy(terkirim = 1, statusPush = "ok")
                        )
                    } else {
                        repo.updateOverrideLokal(
                            override.copy(terkirim = 1, statusPush = "ditolak", pesanPush = response.pesan)
                        )
                    }
                } catch (e: Exception) {
                    // Network error — jangan tandai terkirim, coba lagi nanti
                }
            }

            // 5. Lapor kesehatan (PRD 9.3) — wrapped, tidak gagalkan siklus
            try {
                val jadwalJamLalu = System.currentTimeMillis() - startTime
                api.reportHealth(
                    deviceId,
                    com.smkn2malinau.absensi.data.remote.HealthReportRequest(
                        jadwalJamLalu = jadwalJamLalu,
                        dispensasiJamLalu = 0L
                    )
                )
            } catch (e: Exception) {
                // Kegagalan lapor kesehatan TIDAK menggagalkan siklus — hanya di-log
            }

            // Log sync event
            repo.insertSyncEvent(
                SyncEventLogEntity(
                    timestamp = LocalDateTime.now().format(fmt),
                    durationMs = System.currentTimeMillis() - startTime,
                    status = "success",
                    batchCount = batchCount,
                    successCount = successCount,
                    duplicateCount = duplicateCount,
                    failCount = failCount,
                    errorMessage = null,
                    deviceId = deviceId,
                    createdAt = LocalDateTime.now().format(fmt)
                )
            )

            return SyncResult.Success(
                batchCount = batchCount,
                successCount = successCount,
                duplicateCount = duplicateCount,
                failCount = failCount
            )
        } catch (e: Exception) {
            errorMessage = e.message
            repo.insertSyncEvent(
                SyncEventLogEntity(
                    timestamp = LocalDateTime.now().format(fmt),
                    durationMs = System.currentTimeMillis() - startTime,
                    status = "failed",
                    batchCount = batchCount,
                    successCount = successCount,
                    duplicateCount = duplicateCount,
                    failCount = failCount,
                    errorMessage = errorMessage,
                    deviceId = deviceId,
                    createdAt = LocalDateTime.now().format(fmt)
                )
            )
            return SyncResult.Failure(errorMessage)
        }
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
