package com.smkn2malinau.absensi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Tema aplikasi — palet gelap tunggal dari token kiosk Windows.
 * Dipakai baik oleh KioskScreen maupun AdminScreen supaya konsisten.
 */
private val skemaWarna = darkColorScheme(
    primary = AbsensiColors.Aksen,
    onPrimary = AbsensiColors.Bg,
    primaryContainer = AbsensiColors.Surface2,
    onPrimaryContainer = AbsensiColors.Ink,
    secondary = AbsensiColors.InkSoft,
    background = AbsensiColors.Bg,
    onBackground = AbsensiColors.Ink,
    surface = AbsensiColors.Surface,
    onSurface = AbsensiColors.Ink,
    surfaceVariant = AbsensiColors.Surface2,
    onSurfaceVariant = AbsensiColors.InkSoft,
    outline = AbsensiColors.Border,
    outlineVariant = AbsensiColors.Border,
    error = AbsensiColors.BahayaTeks,
    onError = AbsensiColors.Bg,
    errorContainer = AbsensiColors.BahayaBg,
    onErrorContainer = AbsensiColors.BahayaTeks,
)

private val tipografi = Typography().run {
    val padat = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, lineHeightStyle = padat),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
        labelMedium = TextStyle(
            fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        ),
    )
}

private val bentuk = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm),
    small = androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(Radius.md),
    large = androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg),
)

@Composable
fun AbsensiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = skemaWarna,
        typography = tipografi,
        shapes = bentuk,
        content = content,
    )
}
