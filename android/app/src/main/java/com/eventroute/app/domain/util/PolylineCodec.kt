package com.eventroute.app.domain.util

import com.eventroute.app.domain.model.GeoPoint

object PolylineCodec {
    fun decode(encoded: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        var i = 0
        var lat = 0
        var lng = 0
        while (i < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0
            result = 0
            do {
                b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out += GeoPoint(lat / 1e5, lng / 1e5)
        }
        return out
    }
}
