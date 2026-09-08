import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Baca konfigurasi lokal (tidak di-commit). Dipakai untuk BuildConfig di bawah.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localOrDefault(key: String, default: String): String =
    (localProps.getProperty(key) ?: (project.findProperty(key) as String?) ?: default)

android {
    namespace = "com.smkn2malinau.absensi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smkn2malinau.absensi"
        minSdk = 26
        targetSdk = 35
        // Naikkan tiap rilis supaya device kiosk mengenali APK baru sebagai update.
        versionCode = 6
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Google OAuth Web Client ID (audience untuk Google ID token) — samakan
        // dengan GOOGLE_CLIENT_ID di client Windows. Isi di local.properties:
        //   GOOGLE_WEB_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
        buildConfigField(
            "String", "GOOGLE_WEB_CLIENT_ID",
            "\"${localOrDefault("GOOGLE_WEB_CLIENT_ID", "")}\""
        )
        // Base URL server — bisa di-override dari local.properties (SERVER_BASE_URL=)
        buildConfigField(
            "String", "SERVER_BASE_URL",
            "\"${localOrDefault("SERVER_BASE_URL", "https://absen.smkn2malinau.sch.id/")}\""
        )
        // Fernet key embedding wajah — HARUS sama dengan FACE_ENCRYPTION_KEY server
        // (`cryptography.fernet`, 32 byte base64url). Isi di local.properties:
        //   FACE_ENCRYPTION_KEY=<44 karakter base64url>
        // Bisa juga di-set runtime di layar Setup Device (disimpan di Keystore).
        buildConfigField(
            "String", "FACE_ENCRYPTION_KEY",
            "\"${localOrDefault("FACE_ENCRYPTION_KEY", "")}\""
        )
        // Akun admin OFFLINE default — di-seed sekali saat aplikasi start bila
        // BELUM ada akun lokal sama sekali (break-glass saat server/Google tak
        // terjangkau). Isi di local.properties (TIDAK di-commit):
        //   DEFAULT_ADMIN_USER=namauser
        //   DEFAULT_ADMIN_PASS=passwordkuat
        // Kosong = tidak ada akun default (perilaku lama: setup manual).
        buildConfigField(
            "String", "DEFAULT_ADMIN_USER",
            "\"${localOrDefault("DEFAULT_ADMIN_USER", "")}\""
        )
        buildConfigField(
            "String", "DEFAULT_ADMIN_PASS",
            "\"${localOrDefault("DEFAULT_ADMIN_PASS", "")}\""
        )
    }

    // Signing rilis — kredensial dibaca dari local.properties (TIDAK di-commit),
    // pola sama seperti DEFAULT_ADMIN_* di atas. Isi:
    //   RELEASE_STORE_FILE=D:/path/ke/rilis.jks   (absolut, atau relatif ke folder app/)
    //   RELEASE_STORE_PASSWORD=...
    //   RELEASE_KEY_ALIAS=...
    //   RELEASE_KEY_PASSWORD=...
    // Kalau RELEASE_STORE_FILE kosong / file tak ada → release tetap dibuild
    // TANPA tanda tangan (app-release-unsigned.apk), perilaku lama.
    val releaseStorePath = localOrDefault("RELEASE_STORE_FILE", "")
    val releaseStoreFile = if (releaseStorePath.isNotBlank()) file(releaseStorePath) else null
    signingConfigs {
        if (releaseStoreFile != null && releaseStoreFile.exists()) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localOrDefault("RELEASE_STORE_PASSWORD", "")
                keyAlias = localOrDefault("RELEASE_KEY_ALIAS", "")
                keyPassword = localOrDefault("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            // null bila keystore belum dikonfigurasi → APK tidak ditandatangani.
            signingConfig = signingConfigs.findByName("release")
            // R8/minify DIMATIKAN dengan sengaja: APK ini ~200MB, mayoritas
            // native lib (ONNX Runtime, ML Kit, SQLCipher) + model ML yang
            // TIDAK bisa di-shrink R8. Penghematan kode Java cuma ~5-10MB,
            // tidak sebanding risiko crash HANYA-di-release kalau aturan
            // keep proguard (SQLCipher/Gson JNI & reflection) kurang lengkap.
            // Kecilkan ukuran lewat ABI split di bawah, bukan R8.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Pisah APK per arsitektur CPU — kiosk hardware modern = arm64-v8a.
    // Universal APK tetap dibuat sebagai fallback (jalan di semua device).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Room + SQLCipher (PRD bagian 7)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("net.zetetic:sqlcipher-android:4.17.0")

    // Retrofit + OkHttp (PRD bagian 4 & 8)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Google Sign-In via Credential Manager (registrasi device — setara OAuth Windows)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // WorkManager (PRD bagian 9)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // CameraX (PRD bagian 5)
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    // ONNX Runtime Mobile (PRD bagian 5)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")

    // ML Kit Face Detection — bundled model (offline, tanpa Play Services)
    // untuk crop wajah sebelum MiniFasNet/ArcFace (setara Haar cascade client Windows).
    implementation("com.google.mlkit:face-detection:16.1.7")

    // ML Kit Barcode Scanning — scan QR provisioning device (Setup Device).
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Fused Location Provider — geofencing per device (lihat location/LocationChecker.kt)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Android Keystore wrapper (PRD bagian 5)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // DataStore for SharedPreferences alternative
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // 16 KB page-size aligned native libs
    implementation("androidx.graphics:graphics-path:1.1.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
