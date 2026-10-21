package com.eventroute.app.data.mapper

import com.eventroute.app.data.remote.dto.CollegeDto
import com.eventroute.app.data.remote.dto.EventDto
import com.eventroute.app.domain.model.College
import com.eventroute.app.domain.model.Event
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun CollegeDto.toDomain(): College = College(
    id = id,
    name = name,
    city = city.orEmpty(),
    lat = lat ?: 0.0,
    lng = lng ?: 0.0
)

fun EventDto.toDomain(): Event {
    val zone = try {
        ZoneId.of(timezone ?: "Asia/Kolkata")
    } catch (_: Exception) {
        ZoneId.of("Asia/Kolkata")
    }

    val (formattedDate, formattedTime) = try {
        val instant = Instant.parse(startAt)
        val zonedDateTime = instant.atZone(zone)
        val dateFormatter = DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.ENGLISH)
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a z", Locale.ENGLISH)
        Pair(dateFormatter.format(zonedDateTime), timeFormatter.format(zonedDateTime))
    } catch (_: Exception) {
        Pair(startAt, "")
    }

    return Event(
        id = id,
        title = title,
        organizerName = organizerName,
        college = college.toDomain(),
        category = category,
        bannerUrl = bannerUrl.orEmpty(),
        startAt = startAt,
        checkInAt = checkInAt,
        registrationDeadline = registrationDeadline,
        timezone = timezone ?: "Asia/Kolkata",
        venueName = venueName,
        venueLat = venueLat,
        venueLng = venueLng,
        participationType = participationType,
        teamMin = teamMin,
        teamMax = teamMax,
        seatLimit = seatLimit,
        seatsLeft = seatsLeft,
        feeAmount = feeAmount,
        status = status,
        distanceKm = distanceKm,
        travelEstimateMinutes = travelEstimateMinutes,
        isDemo = isDemo,
        collegeName = college.name,
        formattedDate = formattedDate,
        formattedTime = formattedTime
    )
}
