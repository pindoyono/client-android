package com.smkn2malinau.absensi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Token warna — reuse persis dari kiosk Windows (PRD bagian 6.2).
 * Palet gelap tunggal (kiosk selalu mode gelap).
 */
object AbsensiColors {
    val Ink = Color(0xFFF0F1F3)
    val InkSoft = Color(0xFF9AA1AC)
    val InkMuted = Color(0xFF6B7280)
    val Bg = Color(0xFF0F1115)
    val Surface = Color(0xFF1A1D24)
    val Surface2 = Color(0xFF22262F)
    val Border = Color(0xFF2E333D)

    val Aksen = Color(0xFF60A5FA)

    val SuksesTeks = Color(0xFF4ADE80); val SuksesBg = Color(0xFF14291D)
    val WarningTeks = Color(0xFFFBBF24); val WarningBg = Color(0xFF2E2410)
    val BahayaTeks = Color(0xFFF87171); val BahayaBg = Color(0xFF2E1414)
    val NetralTeks = Color(0xFF9AA1AC); val NetralBg = Color(0xFF20242B)
}

/** Skala spasi tunggal — jaga ritme vertikal konsisten. */
object Spasi {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 40.dp
}

/** Radius sudut. */
object Radius {
    val sm = 10.dp
    val md = 16.dp
    val lg = 28.dp
    val pill = 999.dp
}

/** Pasangan warna untuk satu status hasil scan. */
data class PaletHasil(
    val aksen: Color,
    val latar: Color,
    val label: String,
)
