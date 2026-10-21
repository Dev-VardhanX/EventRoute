package com.eventroute.app

import com.eventroute.app.ui.components.qrMatrix
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test

class QrRoundTripTest {

    private fun decodeMatrix(content: String): String {
        val matrix = qrMatrix(content)
        val width = matrix.width
        val height = matrix.height

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Black module = 0xFF000000, White module = 0xFFFFFFFF
                pixels[y * width + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result = reader.decode(binaryBitmap)
        return result.text
    }

    @Test
    fun testInviteLinkQrRoundTrip() {
        val inviteLink = "https://eventroute.app/invite/HACK-291X"
        val decoded = decodeMatrix(inviteLink)
        assertEquals(inviteLink, decoded)
    }

    @Test
    fun testTicketTokenQrRoundTrip() {
        val ticketToken = "evrt.11111111-1111-1111-1111-111111111111.u-john.8f3a1c"
        val decoded = decodeMatrix(ticketToken)
        assertEquals(ticketToken, decoded)
    }
}
