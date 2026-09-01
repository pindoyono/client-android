package com.smkn2malinau.absensi.data.remote

import retrofit2.http.*

interface ApiService {
    // 1. Sync absensi batch
    @POST("absensi/sync")
    suspend fun syncAbsensi(@Body request: SyncAbsensiRequest): SyncAbsensiResponse

    // 2. Get embeddings (aktif flag)
    @GET("embeddings/sync")
    suspend fun getEmbeddings(): EmbeddingSyncResponse

    // 3. Get effective schedule
    @GET("jadwal/efektif")
    suspend fun getJadwalEfektif(): JadwalEfektifResponse

    // 4. Get active dispensasi
    @GET("dispensasi/aktif")
    suspend fun getDispensasiAktif(): DispensasiAktifResponse

    // 5. Push local schedule override
    @POST("jadwal/override")
    suspend fun pushOverride(@Body request: PushOverrideRequest): PushOverrideResponse

    // 6. Report device health
    @POST("device/{id}/health")
    suspend fun reportHealth(
        @Path("id") deviceId: String,
        @Body request: HealthReportRequest
    ): HealthReportResponse
}
