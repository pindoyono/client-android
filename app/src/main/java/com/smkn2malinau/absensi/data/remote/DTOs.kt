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
    /** ISO datetime penuh — server memvalidasi sebagai `datetime` (mis. "2026-09-04T07:31:05"). */
    @SerializedName("jam_aktual") val jamAktual: String,
    @SerializedName("status_kehadiran_otomatis") val statusKehadiranOtomatis: String,
    @SerializedName("catatan") val catatan: String?,
    @SerializedName("device_id") val deviceId: String,
    /** true = record dibuat saat status geofencing menandai lokasi mock (fake
     *  GPS). Server menyimpan tanda ini (tidak menolak). Default false —
     *  server versi lama mengabaikan field ini. */
    @SerializedName("lokasi_mock") val lokasiMock: Boolean = false,
)

/**
 * Response `POST /absensi/sync` — server mengembalikan ringkasan + hasil per record
 * (`{ total, disimpan, duplikat, gagal, hasil: [...] }`), BUKAN daftar id.
 */
data class SyncAbsensiResponse(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("disimpan") val disimpan: Int = 0,
    @SerializedName("duplikat") val duplikat: Int = 0,
    @SerializedName("gagal") val gagal: Int = 0,
    @SerializedName("hasil") val hasil: List<SyncResultItemDto> = emptyList()
)

data class SyncResultItemDto(
    @SerializedName("record_id") val recordId: String,
    /** disimpan | duplikat_diabaikan | gagal | ditolak_kebijakan */
    @SerializedName("status") val status: String,
    @SerializedName("pesan") val pesan: String? = null
)

/** Response `GET /embeddings/sync` — `{ server_time, jumlah, data: [...] }`. */
data class EmbeddingSyncResponse(
    @SerializedName("server_time") val serverTime: String? = null,
    @SerializedName("jumlah") val jumlah: Int = 0,
    @SerializedName("data") val data: List<SiswaEmbeddingDto> = emptyList()
)

data class SiswaEmbeddingDto(
    @SerializedName("siswa_id") val siswaId: Int,
    @SerializedName("nis") val nis: String,
    @SerializedName("nama") val nama: String,
    @SerializedName("kelas") val kelas: String,
    @SerializedName("aktif") val aktif: Boolean = true,
    /** Embedding terenkripsi, di-encode HEX oleh server (bukan base64). */
    @SerializedName("embedding_encrypted") val embeddingHex: String? = null,
    @SerializedName("model_version") val modelVersion: String = ""
)

/**
 * Item `GET /siswa` (device-auth) — roster siswa aktif LENGKAP, termasuk yang
 * belum enroll wajah. Dipakai layar "Data Siswa" & Enrollment di kiosk;
 * `GET /embeddings/sync` hanya mengirim siswa yang sudah punya embedding.
 */
data class SiswaRosterDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nis") val nis: String,
    @SerializedName("nama") val nama: String,
    @SerializedName("kelas") val kelas: String,
    @SerializedName("enrolled") val enrolled: Boolean = false,
)

/**
 * Response `GET /jadwal/efektif?kelas=` — SATU objek untuk kelas yang diminta
 * (`{ sumber, jam_masuk, jam_pulang, alasan }`). `sumber = "tidak_ada_sekolah"`
 * saat akhir pekan (jam null).
 */
data class JadwalEfektifDto(
    @SerializedName("sumber") val sumber: String = "",
    @SerializedName("jam_masuk") val jamMasuk: String? = null,
    @SerializedName("jam_pulang") val jamPulang: String? = null,
    @SerializedName("alasan") val alasan: String? = null
)

/** `GET /jadwal/standar` — daftar jadwal standar per hari (auth guru). */
data class JadwalStandarDto(
    @SerializedName("hari") val hari: String? = null,
    @SerializedName("kelas") val kelas: String? = null,
    @SerializedName("jam_masuk") val jamMasuk: String? = null,
    @SerializedName("jam_pulang") val jamPulang: String? = null,
)

/** `GET /jadwal/override` — override server per tanggal (auth guru). `id` untuk DELETE. */
data class JadwalOverrideServerDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("tanggal") val tanggal: String? = null,
    @SerializedName("kelas") val kelas: String? = null,
    @SerializedName("jam_masuk") val jamMasuk: String? = null,
    @SerializedName("jam_pulang") val jamPulang: String? = null,
    @SerializedName("alasan") val alasan: String? = null,
)

/** Item `GET /dispensasi/aktif?tanggal=` — server mengembalikan array polos. */
data class DispensasiDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("siswa_id") val siswaId: Int,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("jenis") val jenis: String,
    @SerializedName("kategori") val kategori: String,
    @SerializedName("alasan") val alasan: String? = null,
    @SerializedName("dibuat_oleh") val dibuatOleh: Int? = null
)

data class PushOverrideRequest(
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("kelas") val kelas: String?,
    @SerializedName("jam_masuk") val jamMasuk: String,
    @SerializedName("jam_pulang") val jamPulang: String,
    @SerializedName("alasan") val alasan: String?,
    /** Idempotency key — retry sync dengan client_id sama tidak menggandakan. */
    @SerializedName("client_id") val clientId: String
)

/** Server mengembalikan baris `JadwalOverride` yang dibuat; sukses = HTTP 2xx. */
data class PushOverrideResponse(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("sumber") val sumber: String? = null,
    @SerializedName("client_id") val clientId: String? = null
)

data class HealthReportRequest(
    @SerializedName("jadwal_jam_lalu") val jadwalJamLalu: Double?,
    @SerializedName("dispensasi_jam_lalu") val dispensasiJamLalu: Double?,
    @SerializedName("embedding_hari_lalu") val embeddingHariLalu: Int? = null,
    @SerializedName("pending_kirim") val pendingKirim: Int? = null,
    @SerializedName("app_version") val appVersion: String? = null,
)

data class HealthReportResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("server_time") val serverTime: String? = null,
)

// --- Geofencing — POST /device/{id}/lokasi/cek ---

data class LokasiCekRequest(
    @SerializedName("tersedia") val tersedia: Boolean,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lng") val lng: Double? = null,
    @SerializedName("akurasi_meter") val akurasiMeter: Float? = null,
    @SerializedName("mock") val mock: Boolean = false,
)

data class LokasiCekResponse(
    @SerializedName("valid") val valid: Boolean,
    @SerializedName("alasan") val alasan: String,
    @SerializedName("jarak_meter") val jarakMeter: Double? = null,
    /** Admin sudah pasang titik acuan untuk device ini atau belum — lepas dari `valid`. */
    @SerializedName("dikonfigurasi") val dikonfigurasi: Boolean = false,
)

/** Titik acuan geofencing device ini apa adanya — GET /device/{id}/lokasi, di-cache untuk validasi offline. */
data class LokasiKonfigResponse(
    @SerializedName("lokasi_lat") val lokasiLat: Double? = null,
    @SerializedName("lokasi_lng") val lokasiLng: Double? = null,
    @SerializedName("radius_meter") val radiusMeter: Int? = null,
)

// --- Roster akun (seed login offline) — GET /auth/roster ---

data class RosterResponse(
    @SerializedName("server_time") val serverTime: String? = null,
    @SerializedName("guru") val guru: List<RosterItemDto> = emptyList(),
)

data class RosterItemDto(
    @SerializedName("email") val email: String,
    @SerializedName("nama") val nama: String,
    @SerializedName("role") val role: String,
    @SerializedName("aktif") val aktif: Boolean = true,
)

// --- Registrasi device via Google (setara OAuth client Windows) ---

data class GoogleLoginRequest(
    @SerializedName("google_id_token") val googleIdToken: String
)

data class GoogleLoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("nama") val nama: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("email") val email: String? = null,
    /** Cuma terisi kalau role == "siswa" — dipakai mencocokkan ke siswa_cache lokal (bukan email). */
    @SerializedName("nis") val nis: String? = null,
)

data class DeviceRegisterRequest(
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("nama_lokasi") val namaLokasi: String,
    @SerializedName("platform") val platform: String = "android"
)

data class DeviceRegisterResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("nama_lokasi") val namaLokasi: String? = null,
    @SerializedName("platform") val platform: String? = null,
    @SerializedName("aktif") val aktif: Boolean? = null,
    // Server hanya menampilkan key mentah SEKALI di response ini.
    @SerializedName("raw_api_key") val rawApiKey: String? = null,
    @SerializedName("api_key") val apiKey: String? = null,
    @SerializedName("device_api_key") val deviceApiKey: String? = null,
    /** Fernet key embedding — server kirim di sini supaya client auto-isi (PRD R-P1-1). */
    @SerializedName("face_encryption_key") val faceEncryptionKey: String? = null,
) {
    /** Ambil api key dari nama field manapun yang dipakai server. */
    val apiKeyEfektif: String?
        get() = rawApiKey?.takeIf { it.isNotBlank() }
            ?: apiKey?.takeIf { it.isNotBlank() }
            ?: deviceApiKey?.takeIf { it.isNotBlank() }
}
