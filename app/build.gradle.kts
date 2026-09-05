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
        versionCode = 1
        versionName = "1.0.0"

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
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

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
