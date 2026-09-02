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
├── face/               # FaceEngine + LivenessEvaluator + CryptoEmbedding
├── repository/         # AbsensiRepository — glue business logic <-> DAO (PRD 4)
├── sync/               # SyncService + SyncWorker (WorkManager)
├── audit/              # AuditLogger + LivenessLogger
├── backup/             # BackupManager
├── validation/         # Validation (regex port dari Windows)
├── security/           # CredentialManager (Android Keystore)
├── util/               # NetworkMonitor (status jaringan sungguhan)
└── ui/                 # KioskScreen + KioskViewModel + CameraView + theme
```

## Alur Kiosk (PRD bagian 4)

`CameraView` (CameraX) → `KioskViewModel.onFrameCaptured()` → `FaceEngine.prosesFrame()`
→ `AbsensiRepository.cariSiswaCocok()` (cosine **distance**, PRD 3) → `AttendanceLogic.hitungHasil()`
→ `AbsensiRepository.simpanAbsensi()` (hanya bila `ON_SITE_TESTING_SELESAI = true`) → `KioskUiState`.

`SyncWorker` dijadwalkan dari `AbsensiApp.onCreate()` (periodik 15 menit, butuh jaringan).
Jam & status jaringan di UI di-update sungguhan (bukan hardcode).

## Setup

1. Salin model ONNX ke `app/src/main/assets/models/`:

   ```bash
   cp minifasnet.onnx  app/src/main/assets/models/
   cp arcface.onnx     app/src/main/assets/models/
   ```

2. Konfigurasi endpoint API di `data/remote/ApiClientProvider.kt` (BASE_URL).

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
- `SyncServiceTest` — gagal-sebagian, push override, embeddings/jadwal mendarat di repo
- `KioskViewModelTest` — E2E capture→keputusan→simpan + gerbang uji lapangan (PRD 4, 5, 10)
- `ValidationTest` — regex port dari Windows

Instrumented (`./gradlew connectedAndroidTest`):

- `AbsensiRepositoryDaoTest` — constraint `UNIQUE(siswa_id,tanggal,type)`, `jadwalEfektif`, `statusHariIni`
