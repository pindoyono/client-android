package com.smkn2malinau.absensi.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrClaimTest {

    @Test
    fun `payload valid diparse`() {
        val p = QrClaim.parse("""{"v":1,"server":"https://absen.smk.sch.id","token":"aB3-_xyz"}""")
        assertEquals("https://absen.smk.sch.id", p!!.server)
        assertEquals("aB3-_xyz", p.token)
    }

    @Test
    fun `trailing slash pada server dibuang`() {
        assertEquals("https://x.id", QrClaim.parse("""{"server":"https://x.id/","token":"t"}""")!!.server)
    }

    @Test
    fun `bukan json - null, tidak crash`() {
        assertNull(QrClaim.parse("halo bukan qr"))
        assertNull(QrClaim.parse(""))
        assertNull(QrClaim.parse(null))
        assertNull(QrClaim.parse("12345"))
    }

    @Test
    fun `server bukan http - ditolak`() {
        assertNull(QrClaim.parse("""{"server":"ftp://x.id","token":"t"}"""))
        assertNull(QrClaim.parse("""{"server":"","token":"t"}"""))
    }

    @Test
    fun `token kosong atau hilang - ditolak`() {
        assertNull(QrClaim.parse("""{"server":"https://x.id","token":""}"""))
        assertNull(QrClaim.parse("""{"server":"https://x.id"}"""))
    }
}
