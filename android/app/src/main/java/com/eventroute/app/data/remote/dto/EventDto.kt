package com.eventroute.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollegeDto(
    val id: String,
    val name: String,
    val city: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("email_domain") val emailDomain: String? = null,
    val verified: Boolean = true
)

@Serializable
data class EventDto(
    val id: String,
    val title: String,
    @SerialName("organizer_name") val organizerName: String,
    val college: CollegeDto,
    val category: String,
    val tags: List<String> = emptyList(),
    @SerialName("banner_url") val bannerUrl: String? = null,
    val mode: String = "offline",
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("check_in_at") val checkInAt: String,
    @SerialName("registration_deadline") val registrationDeadline: String,
    val timezone: String? = "Asia/Kolkata",
    @SerialName("venue_name") val venueName: String,
    @SerialName("venue_lat") val venueLat: Double,
    @SerialName("venue_lng") val venueLng: Double,
    @SerialName("participation_type") val participationType: String,
    @SerialName("team_min") val teamMin: Int = 1,
    @SerialName("team_max") val teamMax: Int = 1,
    @SerialName("seat_limit") val seatLimit: Int,
    @SerialName("seats_left") val seatsLeft: Int,
    @SerialName("fee_amount") val feeAmount: Double = 0.0,
    val eligibility: String? = null,
    val status: String = "published",
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("travel_estimate_minutes") val travelEstimateMinutes: Int? = null,
    @SerialName("is_demo") val isDemo: Boolean = false
)

@Serializable
data class EventListResponse(
    val items: List<EventDto>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int
)

@Serializable
data class TravelEstimateRequest(
    @SerialName("origin_lat") val originLat: Double,
    @SerialName("origin_lng") val originLng: Double,
    @SerialName("event_ids") val eventIds: List<String>
)

@Serializable
data class TravelEstimateItem(
    @SerialName("event_id") val eventId: String,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("duration_minutes") val durationMinutes: Int
)

@Serializable
data class TravelEstimateResponse(
    val estimates: List<TravelEstimateItem>
)
