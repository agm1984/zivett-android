package com.zivett.app.design

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/// A QR code for a share link (the referral cards) — ZXing, rendered
/// nearest-neighbor so the modules stay crisp.
@Composable
fun ZQRCode(url: String, size: Dp = 128.dp) {
    val bitmap = remember(url) { render(url) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Your referral QR code — scanning opens the signup page with your code",
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(size).clip(RoundedCornerShape(ZRadius.tile)).border(1.dp, ZTheme.colors.border, RoundedCornerShape(ZRadius.tile)),
        )
    }
}

private fun render(text: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256, mapOf(EncodeHintType.MARGIN to 1))
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
    for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bitmap
}.getOrNull()
