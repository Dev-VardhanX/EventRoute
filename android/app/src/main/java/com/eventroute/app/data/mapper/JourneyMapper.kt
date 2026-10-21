package com.eventroute.app.data.mapper

import com.eventroute.app.data.remote.dto.*
import com.eventroute.app.domain.model.*
import com.eventroute.app.domain.util.PolylineCodec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun PlaceDto.toDomain(): Place = Place(
    label = label,
    lat = lat,
    lng = lng
)

fun BufferBreakdownDto.toDomain(): BufferItem = BufferItem(
    label = label,
    minutes = minutes
)

fun LegDto.toDomain(): Leg {
    val legMode = LegMode.fromString(mode)
    val points = PolylineCodec.decode(polyline)
    val domainSteps = steps.map {
        RouteStep(
            instruction = it.instruction,
            distanceMeters = it.distanceM,
            durationMinutes = if (it.durationS > 0) (it.durationS + 59) / 60 else 0,
            mode = mode
        )
    }
    return Leg(
        mode = legMode,
        from = from.toDomain(),
        to = to.toDomain(),
        durationS = durationS,
        distanceM = distanceM,
        polyline = polyline,
        points = points,
        steps = domainSteps,
        transitLineName = transit?.lineName,
        transitColorHex = transit?.color
    )
}

fun RouteDto.toDomain(): Route = Route(
    id = id,
    summary = summary,
    durationS = durationS,
    distanceM = distanceM,
    departureTime = departureTime,
    arrivalTime = arrivalTime,
    fareText = fare?.let { "₹${it.amount}" },
    transfers = transfers,
    walkingS = walkingS,
    legs = legs.map { it.toDomain() }
)

fun formatIsoToTime(isoString: String, timezone: String = "Asia/Kolkata"): String {
    return try {
        val instant = Instant.parse(isoString)
        val zone = ZoneId.of(timezone)
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
        formatter.format(instant.atZone(zone))
    } catch (_: Exception) {
        isoString
    }
}

fun JourneyPlanResponse.toDomain(timezone: String = "Asia/Kolkata"): Journey {
    val domainRoutes = routes.map { it.toDomain() }
    val selectedRoute = domainRoutes.find { it.id == selectedRouteId } ?: domainRoutes.firstOrNull()

    // Flatten legs into UI steps, inserting transfer steps between consecutive transit legs
    val stepsList = mutableListOf<RouteStep>()
    if (selectedRoute != null) {
        var prevLeg: LegDto? = null
        for (leg in selectedRoute.legs) {
            val isTransit = leg.mode == LegMode.METRO || leg.mode == LegMode.BUS || leg.mode == LegMode.TRAIN
            val prevIsTransit = prevLeg?.let {
                it.mode == "metro" || it.mode == "bus" || it.mode == "train"
            } ?: false

            if (isTransit && prevIsTransit) {
                val nextLine = leg.transitLineName ?: "next line"
                stepsList.add(
                    RouteStep(
                        instruction = "Switch to $nextLine (5 min transfer)",
                        distanceMeters = 200,
                        durationMinutes = 5,
                        mode = "transfer"
                    )
                )
            }

            if (leg.steps.isNotEmpty()) {
                stepsList.addAll(leg.steps)
            } else {
                val instruction = when (leg.mode) {
                    LegMode.WALK -> "Walk to ${leg.to.label}"
                    LegMode.METRO -> "${leg.transitLineName ?: "Metro"} to ${leg.to.label}"
                    LegMode.BUS -> "${leg.transitLineName ?: "Bus"} to ${leg.to.label}"
                    else -> "Travel to ${leg.to.label}"
                }
                stepsList.add(
                    RouteStep(
                        instruction = instruction,
                        distanceMeters = leg.distanceM,
                        durationMinutes = (leg.durationS + 59) / 60,
                        mode = leg.mode.name.lowercase()
                    )
                )
            }
            prevLeg = routes.flatMap { it.legs }.find { it.polyline == leg.polyline }
        }
    }

    val primaryModeSummary = selectedRoute?.summary ?: "Transit"

    return Journey(
        id = planId,
        eventId = eventId,
        originName = origin.label,
        originLat = origin.lat,
        originLng = origin.lng,
        destinationName = destination.label,
        destinationLat = destination.lat,
        destinationLng = destination.lng,
        leaveBy = formatIsoToTime(leaveBy, timezone),
        arriveAt = formatIsoToTime(arriveAt, timezone),
        travelTimeMinutes = selectedRoute?.let { it.durationS / 60 } ?: 45,
        bufferMinutes = bufferMinutes,
        primaryMode = primaryModeSummary,
        steps = stepsList,
        isLeaveNow = mode.equals("leave_now", ignoreCase = true),
        planId = planId,
        routes = domainRoutes,
        bufferBreakdown = bufferBreakdown.map { it.toDomain() }
    )
}
