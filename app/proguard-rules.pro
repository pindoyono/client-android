# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep Room entities
-keep class com.smkn2malinau.absensi.data.local.entity.** { *; }

# Keep Gson model classes
-keep class com.smkn2malinau.absensi.data.remote.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
