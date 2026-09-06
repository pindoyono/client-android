package com.smkn2malinau.absensi.device

import com.google.gson.JsonParser

/**
 * Payload QR "QR Setup" device dari dashboard:
 * `{"v":1,"server":"https://absen.sekolah.sch.id","token":"<acak>"}`.
 * Client memindainya lalu menukar `token` lewat `POST /device/claim`.
 */
object QrClaim {

    data class Payload(val server: String, val token: String)

    /** Parse string QR. Null kalau bukan payload klaim yang valid. */
    fun parse(raw: String?): Payload? {
        if (raw.isNullOrBlank()) return null
        return try {
            val o = JsonParser.parseString(raw.trim()).asJsonObject
            val server = o.get("server")?.takeIf { !it.isJsonNull }?.asString?.trim()?.trimEnd('/')
            val token = o.get("token")?.takeIf { !it.isJsonNull }?.asString?.trim()
            when {
                server.isNullOrBlank() || token.isNullOrBlank() -> null
                !server.startsWith("http://") && !server.startsWith("https://") -> null
                else -> Payload(server, token)
            }
        } catch (e: Exception) {
            null
        }
    }
}
