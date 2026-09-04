package com.smkn2malinau.absensi.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class GoogleIdTokenTest {

    private fun jwt(payloadJson: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"RS256"}""".toByteArray())
        val payload = enc.encodeToString(payloadJson.toByteArray())
        return "$header.$payload.signature-tidak-diverifikasi"
    }

    @Test
    fun `baca email dari payload`() {
        val token = jwt("""{"email":"guru@smkn2malinau.sch.id","hd":"smkn2malinau.sch.id"}""")
        assertEquals("guru@smkn2malinau.sch.id", GoogleIdToken.email(token))
        assertEquals("smkn2malinau.sch.id", GoogleIdToken.hostedDomain(token))
    }

    @Test
    fun `token rusak - null, tidak crash`() {
        assertNull(GoogleIdToken.email("bukan.jwt"))
        assertNull(GoogleIdToken.email(""))
        assertNull(GoogleIdToken.payload("a.b"))
    }

    @Test
    fun `payload tanpa email - null`() {
        assertNull(GoogleIdToken.email(jwt("""{"sub":"123"}""")))
    }
}
