# CATATAN: release saat ini dibuild dengan isMinifyEnabled = false
# (lihat app/build.gradle.kts — APK didominasi native lib, R8 tak berguna).
# Aturan di bawah dipertahankan supaya AMAN kalau minify di-ON-kan lagi.

# ONNX Runtime — dimuat via JNI/refleksi
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Room entities
-keep class com.smkn2malinau.absensi.data.local.entity.** { *; }

# SQLCipher (net.zetetic:sqlcipher-android) — JNI, tak boleh di-rename/strip
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**
-dontwarn net.sqlcipher.**

# Gson + model DTO (refleksi lewat @SerializedName)
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-keep class com.smkn2malinau.absensi.data.remote.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# Retrofit / OkHttp
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
