package com.smkn2malinau.absensi.sync

import com.smkn2malinau.absensi.data.local.entity.*
import com.smkn2malinau.absensi.data.remote.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface SyncRepository {
    suspend fun getUnsyncedRecords(): List<AbsensiLokal>
    suspend fun updateAbsensi(absensi: AbsensiLokal)
    suspend fun insertSiswa(siswa: SiswaCache)
    suspend fun deleteSiswa(siswaId: Int)
    suspend fun insertDispensasi(dispensasi: DispensasiCache)
    suspend fun getUnsyncedOverrides(): List<JadwalOverrideLokal>
    suspend fun updateOverrideLokal(override: JadwalOverrideLokal)
    suspend fun insertSyncEvent(log: SyncEventLog)
    suspend fun insertLiveness(log: LivenessLog)
}

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
            val unsynced = repo.getUnsyncedRecords()
            if (unsynced.isNotEmpty()) {
                batchCount++
                val dtoList = unsynced.map {
                    AbsensiRecordDto(
                        recordId = it.record_id,
                        siswaId = it.siswa_id,
                        tanggal = it.tanggal,
                        type = it.type,
                        jamAktual = it.jam_aktual,
                        statusKehadiranOtomatis = it.status_kehadiran_otomatis,
                        catatan = it.catatan,
                        deviceId = it.device_id
                    )
                }
                val response = api.syncAbsensi(SyncAbsensiRequest(dtoList))
                for (record in unsynced) {
                    if (response.diterima.contains(record.record_id)) {
                        repo.updateAbsensi(
                            record.copy(synced = 1, sync_status = "ok", percobaan_sync = record.percobaan_sync + 1)
                        )
                        successCount++
                    } else if (response.duplikat.contains(record.record_id)) {
                        repo.updateAbsensi(
                            record.copy(synced = 1, sync_status = "duplikat", percobaan_sync = record.percobaan_sync + 1)
                        )
                        duplicateCount++
                    } else {
                        repo.updateAbsensi(
                            record.copy(synced = 0, sync_status = "gagal", percobaan_sync = record.percobaan_sync + 1)
                        )
                        failCount++
                    }
                }
            }

            val embeddingResponse = api.getEmbeddings()
            for (dto in embeddingResponse.siswaList) {
                if (dto.aktif) {
                    repo.insertSiswa(
                        SiswaCache(
                            siswa_id = dto.siswaId,
                            nis = dto.nis,
                            nama = dto.nama,
                            kelas = dto.kelas
                        )
                    )
                } else {
                    repo.deleteSiswa(dto.siswaId)
                }
            }

            val dispensasiResponse = api.getDispensasiAktif()
            for (dto in dispensasiResponse.dispensasiList) {
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

            val unsyncedOverrides = repo.getUnsyncedOverrides()
            for (override in unsyncedOverrides) {
                if (override.status_push == "ditolak") continue
                try {
                    val response = api.pushOverride(
                        PushOverrideRequest(
                            clientId = override.id,
                            tanggal = override.tanggal,
                            kelas = override.kelas,
                            jamMasuk = override.jam_masuk,
                            jamPulang = override.jam_pulang,
                            alasan = override.alasan
                        )
                    )
                    if (response.status == "ok") {
                        repo.updateOverrideLokal(
                            override.copy(terkirim = 1, status_push = "ok")
                        )
                    } else {
                        repo.updateOverrideLokal(
                            override.copy(terkirim = 1, status_push = "ditolak", pesan_push = response.pesan)
                        )
                    }
                } catch (e: Exception) {
                }
            }

            try {
                api.reportHealth(
                    deviceId,
                    HealthReportRequest(
                        jadwalJamLalu = (System.currentTimeMillis() - startTime) / 3600000,
                        dispensasiJamLalu = 0L
                    )
                )
            } catch (e: Exception) {}

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
            errorMessage = e.message
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
