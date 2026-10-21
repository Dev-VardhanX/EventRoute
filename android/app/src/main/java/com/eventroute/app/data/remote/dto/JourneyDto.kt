package com.eventroute.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaceDto(
    val label: String,
    val lat: Double,
    val lng: Double
)

@Serializable
data class FareDto(
    val amount: Int,
    val currency: String = "INR"
)

@Serializable
data class StepDto(
    val instruction: String,
    @SerialName("distance_m") val distanceM: Int = 0,
    @SerialName("duration_s") val durationS: Int = 0
)

@Serializable
data class TransitDto(
    @SerialName("line_name") val lineName: String,
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("vehicle_type") val vehicleType: String,
    val headsign: String? = null,
    @SerialName("stops_count") val stopsCount: Int = 0,
    val agency: String? = null,
    val color: String? = null
)

@Serializable
data class LegDto(
    val mode: String, // walk|bus|metro|train|tram|bike|drive
    val from: PlaceDto,
    val to: PlaceDto,
    @SerialName("duration_s") val durationS: Int,
    @SerialName("distance_m") val distanceM: Int,
    val polyline: String, // Google encoded polyline, precision 5
    val steps: List<StepDto> = emptyList(),
    val transit: TransitDto? = null
)

@Serializable
data class RouteDto(
    val id: String,
    val summary: String,
    @SerialName("duration_s") val durationS: Int,
    @SerialName("distance_m") val distanceM: Int,
    @SerialName("departure_time") val departureTime: String? = null,
    @SerialName("arrival_time") val arrivalTime: String? = null,
    val fare: FareDto? = null,
    val transfers: Int = 0,
    @SerialName("walking_s") val walkingS: Int = 0,
    val legs: List<LegDto>
)

@Serializable
data class BufferBreakdownDto(
    val label: String,
    val minutes: Int
)

@Serializable
data class JourneyPlanResponse(
    @SerialName("plan_id") val planId: String,
    @SerialName("event_id") val eventId: String,
    val origin: PlaceDto,
    val destination: PlaceDto,
    @SerialName("check_in_at") val checkInAt: String,
    @SerialName("leave_by") val leaveBy: String,
    @SerialName("arrive_at") val arriveAt: String,
    @SerialName("buffer_minutes") val bufferMinutes: Int = 0,
    @SerialName("buffer_breakdown") val bufferBreakdown: List<BufferBreakdownDto> = emptyList(),
    val mode: String = "normal", // normal | leave_now
    @SerialName("selected_route_id") val selectedRouteId: String? = null,
    val routes: List<RouteDto> = emptyList()
)

typealias JourneyDto = JourneyPlanResponse

@Serializable
data class SelectJourneyPlanRequest(
    @SerialName("plan_id") val planId: String,
    @SerialName("route_id") val routeId: String? = null
)
