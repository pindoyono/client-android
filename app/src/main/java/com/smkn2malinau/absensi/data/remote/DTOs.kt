package com.smkn2malinau.absensi.data.remote

import com.google.gson.annotations.SerializedName

data class SyncAbsensiRequest(
    @SerializedName("records") val records: List<AbsensiRecordDto>
)

data class AbsensiRecordDto(
    @SerializedName("record_id") val recordId: String,
    @SerializedName("siswa_id") val siswaId: Int,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("type") val type: String,
    @SerializedName("jam_aktual") val jamAktual: String,
    @SerializedName("status_kehadiran_otomatis") val statusKehadiranOtomatis: String,
    @SerializedName("catatan") val catatan: String?,
    @SerializedName("device_id") val deviceId: String
)

data class SyncAbsensiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("diterima") val diterima: List<String>,
    @SerializedName("duplikat") val duplikat: List<String>,
    @SerializedName("gagal") val gagal: List<String>
)

data class EmbeddingSyncResponse(
    @SerializedName("siswa") val siswaList: List<SiswaEmbeddingDto>
)

data class SiswaEmbeddingDto(
    @SerializedName("siswa_id") val siswaId: Int,
    @SerializedName("nis") val nis: String,
    @SerializedName("nama") val nama: String,
    @SerializedName("kelas") val kelas: String,
    @SerializedName("embedding_base64") val embeddingBase64: String?,
    @SerializedName("model_version") val modelVersion: String,
    @SerializedName("aktif") val aktif: Boolean = true
)

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

data class DispensasiAktifResponse(
    @SerializedName("dispensasi") val dispensasiList: List<DispensasiDto>
)

data class DispensasiDto(
    @SerializedName("siswa_id") val siswaId: Int,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("jenis") val jenis: String,
    @SerializedName("kategori") val kategori: String,
    @SerializedName("alasan") val alasan: String?
)

data class PushOverrideRequest(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("kelas") val kelas: String?,
    @SerializedName("jam_masuk") val jamMasuk: String,
    @SerializedName("jam_pulang") val jamPulang: String,
    @SerializedName("alasan") val alasan: String?
)

data class PushOverrideResponse(
    @SerializedName("status") val status: String,
    @SerializedName("pesan") val pesan: String?
)

data class HealthReportRequest(
    @SerializedName("jadwal_jam_lalu") val jadwalJamLalu: Long,
    @SerializedName("dispensasi_jam_lalu") val dispensasiJamLalu: Long
)

data class HealthReportResponse(
    @SerializedName("status") val status: String
)
