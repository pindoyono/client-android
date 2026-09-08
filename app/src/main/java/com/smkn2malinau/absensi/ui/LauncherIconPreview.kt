package com.smkn2malinau.absensi.ui

import android.graphics.drawable.Drawable
import com.smkn2malinau.absensi.R
import androidx.compose.foundation.Image
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Preview launcher icon (adaptive icon) sebagai gambar — dipakai untuk ekspor PNG.
 * Bukan bagian dari UI produksi.
 */
@Preview(name = "Launcher 512", widthDp = 512, heightDp = 512, showBackground = true)
@Preview(name = "Launcher 192", widthDp = 192, heightDp = 192, showBackground = true)
@Composable
fun LauncherIconPreview() {
    val context = LocalContext.current
    val iconBitmap = remember {
        val d: Drawable? = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        d?.toBitmap(512, 512)
    }
    Surface(color = Color.White) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Launcher icon",
            )
        }
    }
}