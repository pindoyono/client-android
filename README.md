# Absensi Client Android

Client Android untuk sistem absensi wajah SMKN 2 Malinau — port 1:1 dari kiosk Windows.

## Prasyarat

- Android Studio (versi stabil terbaru)
- Kotlin 1.9+
- minSdk 26 (Android 8.0)
- Gradle Kotlin DSL

## Struktur Project

```
app/src/main/java/com/smkn2malinau/absensi/
├── data/
│   ├── local/          # Room + SQLCipher (10 tabel)
│   │   ├── entity/
│   │   └── dao/
│   └── remote/         # Retrofit + DeviceAuthInterceptor + RateLimiter
├── business/           # AttendanceLogic (state machine PRD 3)
├── device/             # DeviceRegistrar + GoogleIdToken(Provider) — registrasi via Google
├── face/               # FaceEngine + LivenessEvaluator + CryptoEmbedding
├── repository/         # AbsensiRepository (kiosk) + AdminRepository (panel admin)
├── sync/               # SyncService + SyncWorker (WorkManager, + enqueueSekali)
├── audit/              # AuditLogger + LivenessLogger
├── backup/             # BackupManager
├── validation/         # Validation (regex port dari Windows)
├── security/           # CredentialManager (Android Keystore)
├── util/               # NetworkMonitor (status jaringan sungguhan)
└── ui/
    ├── KioskScreen + KioskViewModel + CameraView + EnrollmentScreen + theme
    └── admin/          # Panel Admin ber-section (NavigationRail) + GerbangAdmin (PIN/Google)
```

## Orientasi

**Portrait-first** (`android:screenOrientation="portrait"`) — tablet kiosk dipasang tegak, rasio
cocok dengan wajah orang berdiri, framing kamera lebih natural. Panel Admin pakai
**ModalNavigationDrawer** (hamburger) bukan rail samping.

## Panel Admin (paritas client Windows)

## Auth & Role (`auth/`)

**Role ditentukan server** (`Guru.role` di dashboard). Client tidak menentukan siapa admin.

Buka dari ikon Settings di kiosk → **LoginScreen**:
- **Online**: Login Google → server `/auth/login/google` → role. Akun di-upsert ke `akun_lokal`
  (email + nama + role, tanpa password).
- **Offline**: email/NIS + password → cocokkan hash PBKDF2 di `akun_lokal` (SQLCipher).
  Server **tidak** menyimpan password — akun & hash hanya di device.
- **Seeding**: (a) saat device di-setup lewat Google, akun yang mendaftarkan device otomatis
  masuk `akun_lokal` dgn role server; (b) tiap siklus sync menarik `GET /auth/roster` →
  **semua** guru aktif ter-seed (bisa login offline di device mana pun). Akun tanpa password
  → login offline pertama diminta **buat password**.
- **Tanpa Google** (`GOOGLE_WEB_CLIENT_ID` kosong): Setup Device punya field opsional
  "Email admin lokal + password" sebagai fallback.
- Sesi persist 8 jam (`CredentialManager` terenkripsi), di-clear saat "Logout & tutup".

Role & hak akses (`Role` + `HakAkses`):

| Role | Bisa akses |
|---|---|
| **admin** | semua section + **Akun** (kelola pengguna, set password) + Pengaturan + Perangkat + hapus kredensial |
| **guru_piket** | Sinkronisasi, Jadwal, Data Siswa, Daftar Wajah, Dashboard Web |
| **siswa** | **hanya** layar Riwayat Absensi sendiri (read-only, dari `absensi_lokal` by `siswa_id`) — tidak bisa buka Panel Admin |

Drawer Panel Admin → section (difilter per role):

| Section | Fungsi | Offline |
|---|---|---|
| Sinkronisasi | kartu stat, % progres, "sync terakhir" + error, **Sync sekarang**, 20 record terbaru | baca offline; sync butuh jaringan |
| Jadwal | jadwal cache + **override lokal**: tambah/hapus, reset push ditolak | ✅ override lokal berlaku **langsung** |
| Data Siswa | tabel NIS/nama/kelas/wajah, cari | ✅ |
| Daftar Wajah | cari siswa dari `siswa_cache` → pilih → ambil wajah → embedding pada `siswa_id` server | ✅ |
| Akun | *(admin)* tambah/nonaktifkan akun, set password offline, role | ✅ |
| Perangkat | *(admin)* setup / daftar ulang kredensial, nama lokasi, mode testing | ✅ |
| Pengaturan | *(admin)* `SERVER_BASE_URL`, Face Key + Tes, ambang match, lensa kamera, hapus kredensial | ✅ |
| Dashboard Web | buka `front.<domain>/dashboard/*` di browser | butuh jaringan |

**Daftar Wajah**: data siswa diambil dari `siswa_cache` (hasil `GET /embeddings/sync`), bukan
input manual. Operator cari nama/NIS → pilih → ambil wajah; embedding disimpan terhadap
`siswa_id` **server** (positif), jadi saat sync berikutnya versi server menimpa bersih via PK.
`EnrollmentViewModel.idEnrollLokal` (id negatif, jalur enroll manual) dipertahankan untuk
kompatibilitas tapi tidak dipakai alur ini.

## Alur Kiosk (PRD bagian 4)

`CameraView` (CameraX) → `KioskViewModel.onFrameCaptured()` → `FaceEngine.prosesFrame()`
→ `AbsensiRepository.cariSiswaCocok()` (cosine **distance**, PRD 3) → `AttendanceLogic.hitungHasil()`
→ `AbsensiRepository.simpanAbsensi()` (hanya bila `ON_SITE_TESTING_SELESAI = true`) → `KioskUiState`.

**Kapan absen naik ke server?** Absen disimpan lokal **seketika** (offline-first). Pengiriman:
- **tiap absen tersimpan** → picu `SyncWorker.enqueueSekali` (debounce 10 dtk untuk absen beruntun)
- **loop 90 dtk** selama kiosk aktif (jaring pengaman + retry + tarik data baru)
- **periodik 15 mnt** (`SyncWorker.schedule`, minimum WorkManager) — backstop saat app di-background
- saat kiosk/enrollment dibuka + tombol "Sync sekarang" (paksa REPLACE)

Semua butuh jaringan; offline → absen aman di lokal, naik otomatis saat online.
Jam & status jaringan di UI di-update sungguhan (bukan hardcode).

**Enkripsi embedding** (`CryptoEmbedding`) — **format Fernet**, identik server
(`cryptography.fernet`, AES-128-CBC + HMAC-SHA256) & client Windows; float di-pack
**little-endian**. `FACE_ENCRYPTION_KEY` (Fernet key `.env` server): **otomatis terisi**
dari response `POST /device/register` (PRD R-P1-1) saat setup Google; atau di-set manual di
**Setup Device / Panel Admin → Pengaturan** (Keystore) / `local.properties`.
Key salah/kosong → embedding server tak terdekripsi → semua wajah "tidak dikenali".

**FaceEngine** (`MiniFasNetEngine`) — alur disamakan dgn `client-windows`:
1. **ML Kit Face Detection** (bundled, offline) deteksi wajah terbesar → crop kotak
   + margin 20% (menggantikan Haar cascade Windows). Tak ada wajah → `wajah_tidak_terdeteksi`.
2. Normalisasi: liveness (MiniFasNet) `x/255`, embedding (ArcFace) `(x-127.5)/128`, NCHW.
3. Skor liveness = softmax → kelas "asli" (`INDEKS_KELAS_LIVE = 2`), clamp `[0,1]`.

`prosesFrame` mendeteksi wajah **sekali** lalu pakai crop yg sama utk liveness + embedding
(crop = kotak ML Kit apa adanya, `MARGIN_WAJAH = 0` — samakan dgn Haar Windows).
**Enrollment melewati cek liveness** (`prosesFrameEnroll`, setara `skip_liveness=True` Windows).

**Kalibrasi matching**: ambang *distance* (`0.3542` default Windows) bisa diubah runtime di
**Panel Admin → Pengaturan → Ambang match wajah** (`CredentialManager.getAmbangJarak`, 0.20–0.80).
Kartu "Tidak dikenali" menampilkan `Terdekat X dari ambang Y · N wajah dibanding` untuk bantu
pilih ambang. `N = 0` → `FACE_ENCRYPTION_KEY` salah / belum sync.
⚠️ Ambang liveness `0.752` masih placeholder — kalibrasi ulang untuk kamera Android.

**Kontrak sync** — `SyncService` mengikuti server (`absensi-server-fase1`) & `client-windows`:
- `POST /absensi/sync` → response `{total, disimpan, duplikat, gagal, hasil:[{record_id,status}]}`;
  `jam_aktual` dikirim sebagai datetime penuh (`YYYY-MM-DDTHH:mm:ss`).
- `GET /embeddings/sync` → `{server_time, jumlah, data:[…]}`, `embedding_encrypted` = **hex**.
- `GET /jadwal/efektif?kelas=` → **satu objek** per kelas; ditarik untuk jadwal umum + tiap kelas.
- `GET /dispensasi/aktif?tanggal=` (wajib) → **array**.
- Error siklus sync terakhir ditampilkan di Panel Admin (dari `sync_event_log`).

**Status bar kiosk** (setara header kiosk Windows) — `AbsensiRepository.ringkasanKiosk()`
di-refresh tiap 15 dtk: `Sync: dd/MM HH:mm · N antre, N wajah, N jadwal`, chip
`Masuk: --:--  Pulang: --:--` (jadwal umum hari ini / jadwal pertama tersedia), dan
badge kesegaran (`✓ Data segar` / `⚠ Jadwal & Wajah basi`, ambang 6 jam / 3 hari).
Pil kiri-atas menggabungkan jaringan + **hasil siklus sync terakhir**: `Online · tersinkron`
hanya bila siklus sync terakhir sukses; `Online · belum tersinkron` bila gagal/belum jalan;
`Offline · disimpan lokal` bila tak ada jaringan. Kiosk memicu satu `SyncWorker.enqueueSekali`
saat dibuka.

## Setup

1. Salin model ONNX ke `app/src/main/assets/models/`:

   ```bash
   cp minifasnet.onnx  app/src/main/assets/models/
   cp arcface.onnx     app/src/main/assets/models/
   ```

2. Konfigurasi di `local.properties` (tidak di-commit):

   ```properties
   # Base URL server (opsional — default https://absen.smkn2malinau.sch.id/)
   SERVER_BASE_URL=https://absen.smkn2malinau.sch.id/

   # Google OAuth Web Client ID — WAJIB untuk tombol "Daftar dengan Google".
   # Samakan dengan GOOGLE_CLIENT_ID di client Windows (tipe "Web application"
   # di Google Cloud Console). Kalau kosong, hanya input manual yang tersedia.
   GOOGLE_WEB_CLIENT_ID=xxxxxxxxxxxx.apps.googleusercontent.com

   # Fernet key embedding wajah — HARUS sama PERSIS dengan FACE_ENCRYPTION_KEY
   # di .env server. Tanpa ini semua wajah dari server "tidak dikenali".
   # Bisa juga di-set runtime di Panel Admin → Pengaturan (tapi hilang tiap
   # uninstall — di sini permanen). 44 karakter base64url.
   FACE_ENCRYPTION_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx=
   ```

   Nilai ini diekspos lewat `BuildConfig` (bukan hardcode di source).
   Verifikasi key: Panel Admin → Pengaturan → **Tes Face Key**.

3. Build:

   ```bash
   ./gradlew assembleDebug
   ```

4. Test:
   ```bash
   ./gradlew test
   ```

## Fitur

- **Offline-first**: absensi disimpan lokal, sync saat online
- **Override jadwal lokal** (PRD 9.1): dibuat di device, push ke server
- **Pembersihan siswa nonaktif** (PRD 9.2): hapus cache saat `aktif=false`
- **Lapor kesehatan device** (PRD 9.3): tidak menggagalkan siklus sync
- **Audit/liveness/sync logging** (PRD 9.4): 3 tabel log wajib terisi
- **Safety gate** (PRD 10): `ON_SITE_TESTING_SELESAI` default `false`
- **Auth sederhana** (PRD 4): 1 kredensial `X-Device-Api-Key`, tanpa JWT/HMAC

## Registrasi Device (setara OAuth client Windows)

Dua jalur di layar Admin:

1. **Daftar dengan Google** — otomatis:
   `Google Sign-In (Credential Manager)` → ID token → `POST /auth/login/google` → JWT
   → `POST /device/register` (Bearer JWT) → `raw_api_key` → simpan di Android Keystore.
   Hanya email domain `smkn2malinau.sch.id`, `guru.smk.belajar.id`, `admin.smk.belajar.id`
   (guard di client; verifikasi tetap di server).
2. **Manual** — admin menempel `device_id` + `api_key` dari dashboard (fallback, atau saat
   server balas `409` = device sudah terdaftar).

Setelah tersimpan, semua request pakai header `X-Device-Id` + `X-Device-Api-Key` (tanpa JWT).

## Safety Gate

Selama `ON_SITE_TESTING_SELESAI = false`:

- Banner "MODE TESTING" tampil di layar kiosk
- Wajah tetap dikenali tapi hasil TIDAK disimpan ke database

Ubah ke `true` hanya setelah uji lapangan fisik selesai (PRD bagian 10).

## Test

Unit (JVM — `./gradlew test`):

- `AttendanceLogicTest` — tabel skenario PRD 3.4
- `LivenessEvaluatorTest` — evaluasi liveness + `cocokkanWajah` pakai definisi **distance** (PRD 3)
- `ApiClientTest` — MockWebServer, verifikasi header auth
- `SyncServiceTest` — hasil per-record, `jam_aktual` datetime, embedding hex, jadwal per kelas, push override
- `KioskViewModelTest` — E2E capture→keputusan→simpan + gerbang uji lapangan (PRD 4, 5, 10)
- `DeviceRegistrarTest` — registrasi Google sukses / 409 / guard domain
- `GoogleIdTokenTest` — parsing payload JWT, token rusak tidak crash
- `AdminPanelViewModelTest` — kartu stat terisi, validasi + aksi override lokal
- `EnrollmentIdTest` — id enroll lokal selalu negatif & deterministik
- `ValidationTest` — regex port dari Windows

Instrumented (`./gradlew connectedAndroidTest`):

- `AbsensiRepositoryDaoTest` — constraint `UNIQUE(siswa_id,tanggal,type)`, `jadwalEfektif`, `statusHariIni`
- `AdminRepositoryDaoTest` — statistik sync, override lokal offline → `jadwalEfektif`, reset push, enroll lokal ditimpa server
