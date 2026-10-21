package com.eventroute.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

fun qrMatrix(content: String): BitMatrix = QRCodeWriter().encode(
    content, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 0
    )
)

/**
 * Real claymorphism QR Code composable using ZXing BitMatrix.
 * Draws dark #1E293B modules on the light well with integer module sizing,
 * module corner radius at 20% (<25%) for scannability, and >=4 module quiet zone.
 */
@Composable
fun ClayQrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    val matrix = remember(content) {
        runCatching { qrMatrix(content.ifEmpty { "https://eventroute.app" }) }
            .getOrElse { qrMatrix("https://eventroute.app") }
    }

    Box(
        modifier = modifier
            .size(size)
            .clay(RoundedCornerShape(24.dp), Color.White, depth = 8.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val matrixDim = matrix.width
            val quietZoneModules = 4
            val totalModules = matrixDim + (quietZoneModules * 2)

            // Integer module size in pixels
            val modulePx = (this.size.minDimension / totalModules).toInt().toFloat().coerceAtLeast(1f)
            val qrPx = matrixDim * modulePx

            val startX = (this.size.width - qrPx) / 2f
            val startY = (this.size.height - qrPx) / 2f
            val cornerRadius = modulePx * 0.20f // 20% of module size <= 25%

            for (y in 0 until matrixDim) {
                for (x in 0 until matrixDim) {
                    if (matrix.get(x, y)) {
                        drawRoundRect(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(startX + x * modulePx, startY + y * modulePx),
                            size = Size(modulePx, modulePx),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                        )
                    }
                }
            }
        }
    }
}
