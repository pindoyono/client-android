package com.smkn2malinau.absensi.device

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * Pembacaan payload Google ID token (JWT) — HANYA untuk UX guard (cek domain email).
 * TIDAK memverifikasi signature; verifikasi asli tetap di server (`/auth/login/google`).
 *
 * Pakai `java.util.Base64` + Gson (bukan `android.util.Base64` / `org.json`) supaya
 * bisa diuji di JVM tanpa emulator.
 */
object GoogleIdToken {

    fun payload(idToken: String): JsonObject? {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val json = String(Base64.getUrlDecoder().decode(padBase64(parts[1])))
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    fun email(idToken: String): String? =
        payload(idToken)?.get("email")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    fun hostedDomain(idToken: String): String? =
        payload(idToken)?.get("hd")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun padBase64(s: String): String {
        val rem = s.length % 4
        return if (rem == 0) s else s + "=".repeat(4 - rem)
    }
}
