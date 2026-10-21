package com.eventroute.app

import com.eventroute.app.domain.util.PolylineCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineCodecTest {

    @Test
    fun testGoogleReferenceVector() {
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val points = PolylineCodec.decode(encoded)

        assertEquals(3, points.size)

        // Point 1: (38.5, -120.2)
        assertEquals(38.5, points[0].lat, 0.001)
        assertEquals(-120.2, points[0].lng, 0.001)

        // Point 2: (40.7, -120.95)
        assertEquals(40.7, points[1].lat, 0.001)
        assertEquals(-120.95, points[1].lng, 0.001)

        // Point 3: (43.252, -126.453)
        assertEquals(43.252, points[2].lat, 0.001)
        assertEquals(-126.453, points[2].lng, 0.001)
    }

    @Test
    fun testJourneyAppendixAPolylines() {
        val polylines = listOf(
            "wwrmD_geuMgOjMwQjCgYjC",
            "_utmD{oduMkMkiAkMgiB_NgiBoi@_|Bgw@_jA_l@ogA",
            "opzmDg_vuMfY_`AfpAgiBfiBw|Af{CgpA~{B_q@noBg^",
            "_qgmDwjcvMvQgEjRwB"
        )

        for ((index, poly) in polylines.withIndex()) {
            val decoded = PolylineCodec.decode(poly)
            assertTrue("Polyline $index must be non-empty", decoded.isNotEmpty())

            for (pt in decoded) {
                assertTrue(
                    "Latitude ${pt.lat} in leg $index must be in 28.5..28.7",
                    pt.lat in 28.5..28.7
                )
                assertTrue(
                    "Longitude ${pt.lng} in leg $index must be in 77.0..77.2",
                    pt.lng in 77.0..77.2
                )
            }
        }
    }
}
