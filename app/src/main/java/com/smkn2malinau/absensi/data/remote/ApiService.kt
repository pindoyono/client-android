package com.smkn2malinau.absensi.data.remote

import retrofit2.http.*

interface ApiService {
    // 0a. Login Google (tukar Google ID token → JWT). Tanpa header device.
    @POST("auth/login/google")
    suspend fun loginGoogle(@Body request: GoogleLoginRequest): GoogleLoginResponse

    // 0b. Registrasi device (butuh Bearer JWT dari loginGoogle, bukan X-Device-*).
    @POST("device/register")
    suspend fun registerDevice(
        @Header("Authorization") bearer: String,
        @Body request: DeviceRegisterRequest
    ): DeviceRegisterResponse

    // 1. Sync absensi batch
    @POST("absensi/sync")
    suspend fun syncAbsensi(@Body request: SyncAbsensiRequest): SyncAbsensiResponse

    // 2. Get embeddings — `diperbarui_sejak` opsional/null (delta sync)
    @GET("embeddings/sync")
    suspend fun getEmbeddings(
        @Query("diperbarui_sejak") diperbaruiSejak: String?
    ): EmbeddingSyncResponse

    // 3. Get effective schedule untuk satu kelas (kelas null = jadwal umum)
    @GET("jadwal/efektif")
    suspend fun getJadwalEfektif(@Query("kelas") kelas: String?): JadwalEfektifDto

    // 4. Get active dispensasi — `tanggal` WAJIB (YYYY-MM-DD)
    @GET("dispensasi/aktif")
    suspend fun getDispensasiAktif(@Query("tanggal") tanggal: String): List<DispensasiDto>

    // 5. Push local schedule override
    @POST("jadwal/override")
    suspend fun pushOverride(@Body request: PushOverrideRequest): PushOverrideResponse

    // 6. Report device health
    @POST("device/{id}/health")
    suspend fun reportHealth(
        @Path("id") deviceId: String,
        @Body request: HealthReportRequest
    ): HealthReportResponse

    // 7. Roster guru untuk seed login offline (device-auth)
    @GET("auth/roster")
    suspend fun getRoster(): RosterResponse

    // 8. Roster siswa aktif LENGKAP (device-auth) — termasuk yang belum enroll wajah
    @GET("siswa")
    suspend fun getSiswaRoster(
        @Query("kelas") kelas: String? = null,
        @Query("enrolled") enrolled: Boolean? = null,
    ): List<SiswaRosterDto>

    // 9. Cek geofencing — dipanggil berkala (bukan per-scan), lihat KioskViewModel.
    @POST("device/{id}/lokasi/cek")
    suspend fun cekLokasi(
        @Path("id") deviceId: String,
        @Body request: LokasiCekRequest,
    ): LokasiCekResponse

    // 10. Tarik konfigurasi lokasi sendiri (titik acuan + radius apa adanya) —
    // di-cache lokal untuk validasi jarak mandiri (GeoOffline) saat offline.
    @GET("device/{id}/lokasi")
    suspend fun getLokasiKonfig(@Path("id") deviceId: String): LokasiKonfigResponse
}
