package com.smkn2malinau.absensi.data.remote

import com.google.gson.annotations.SerializedName

// --- DTOs untuk Absensi Sync ---
data class SyncAbsensiRequest(
    @SerializedName("records") val records: List<AbsensiRecordDto>
)

data class AbsensiRecordDto(
    @SerializedName("record_id") val recordId: String,
    @SerializedName("siswa_id") val siswaId: Long,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("type") val type: String,
    @SerializedName("jam_aktual") val jamAktual: String,
    @SerializedName("status_kehadiran_otomatis") val statusKehadiranOtomatis: String,
    @SerializedName("catatan") val catatan: String?,
    @SerializedName("device_id") val deviceId: String
)

data class SyncAbsensiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("diterima") val diterima: List<String>, // list of record_id yang berhasil
    @SerializedName("duplikat") val duplikat: List<String>,
    @SerializedName("gagal") val gagal: List<String>
)

// --- DTOs untuk Embedding Sync ---
data class EmbeddingSyncResponse(
    @SerializedName("siswa") val siswaList: List<SiswaEmbeddingDto>
)

data class SiswaEmbeddingDto(
    @SerializedName("siswa_id") val siswaId: Long,
    @SerializedName("nis") val nis: String,
    @SerializedName("nama") val nama: String,
    @SerializedName("kelas") val kelas: String,
    @SerializedName("embedding_base64") val embeddingBase64: String?,
    @SerializedName("model_version") val modelVersion: String,
    @SerializedName("aktif") val aktif: Boolean = true // PRD 9.2: field aktif
)

// --- DTOs untuk Jadwal Efektif ---
data class JadwalEfektifResponse(
    @SerializedName("jadwal") val jadwalList: List<JadwalDto>
)

data class JadwalDto(
    @SerializedName("kelas") val kelas: String,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("hari") val hari: String,
    @SerializedName("jam_masuk") val jamMasuk: String,
    @SerializedName("jam_pulang") val jamPulang: String,
    @SerializedName("sumber") val sumber: String
)

// --- DTOs untuk Dispensasi Aktif ---
data class DispensasiAktifResponse(
    @SerializedName("dispensasi") val dispensasiList: List<DispensasiDto>
)

data class DispensasiDto(
    @SerializedName("siswa_id") val siswaId: Long,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("jenis") val jenis: String,
    @SerializedName("kategori") val kategori: String,
    @SerializedName("alasan") val alasan: String?
)

// --- DTOs untuk Push Override Jadwal (PRD 9.1) ---
data class PushOverrideRequest(
    @SerializedName("client_id") val clientId: String, // UUID
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("kelas") val kelas: String?,
    @SerializedName("jam_masuk") val jamMasuk: String,
    @SerializedName("jam_pulang") val jamPulang: String,
    @SerializedName("alasan") val alasan: String?
)

data class PushOverrideResponse(
    @SerializedName("status") val status: String, // "ok" | "ditolak"
    @SerializedName("pesan") val pesan: String?
)

// --- DTOs untuk Lapor Kesehatan Device (PRD 9.3) ---
data class HealthReportRequest(
    @SerializedName("jadwal_jam_lalu") val jadwalJamLalu: Long,
    @SerializedName("dispensasi_jam_lalu") val dispensasiJamLalu: Long
)

data class HealthReportResponse(
    @SerializedName("status") val status: String
)
