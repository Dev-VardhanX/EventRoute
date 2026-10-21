package com.eventroute.app.domain.model

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val mode: String // "walk", "metro", "bus", "cab", "drive", "transfer"
)

enum class LegMode {
    WALK, BUS, METRO, TRAIN, TRAM, BIKE, DRIVE, OTHER;

    companion object {
        fun fromString(mode: String): LegMode = when (mode.lowercase()) {
            "walk" -> WALK
            "bus" -> BUS
            "metro" -> METRO
            "train" -> TRAIN
            "tram" -> TRAM
            "bike" -> BIKE
            "drive", "cab" -> DRIVE
            else -> OTHER
        }
    }
}

data class Place(
    val label: String,
    val lat: Double,
    val lng: Double
)

data class Leg(
    val mode: LegMode,
    val from: Place,
    val to: Place,
    val durationS: Int,
    val distanceM: Int,
    val polyline: String,
    val points: List<GeoPoint> = emptyList(),
    val steps: List<RouteStep> = emptyList(),
    val transitLineName: String? = null,
    val transitColorHex: String? = null
)

data class Route(
    val id: String,
    val summary: String,
    val durationS: Int,
    val distanceM: Int,
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val fareText: String? = null,
    val transfers: Int = 0,
    val walkingS: Int = 0,
    val legs: List<Leg> = emptyList()
)

data class BufferItem(
    val label: String,
    val minutes: Int
)

data class Journey(
    val id: String,
    val eventId: String,
    val originName: String,
    val originLat: Double,
    val originLng: Double,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val leaveBy: String,
    val arriveAt: String,
    val travelTimeMinutes: Int,
    val bufferMinutes: Int,
    val primaryMode: String,
    val steps: List<RouteStep> = emptyList(),
    val isLeaveNow: Boolean = false,
    val planId: String = id,
    val routes: List<Route> = emptyList(),
    val bufferBreakdown: List<BufferItem> = emptyList()
)
