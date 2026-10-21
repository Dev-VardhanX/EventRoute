package com.eventroute.app.domain.model

data class College(
    val id: String,
    val name: String,
    val city: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

data class Event(
    val id: String,
    val title: String,
    val organizerName: String,
    val college: College,
    val category: String,
    val bannerUrl: String = "",
    val startAt: String,
    val checkInAt: String,
    val registrationDeadline: String,
    val timezone: String = "Asia/Kolkata",
    val venueName: String,
    val venueLat: Double,
    val venueLng: Double,
    val participationType: String, // "solo", "team", "either"
    val teamMin: Int = 1,
    val teamMax: Int = 1,
    val seatLimit: Int,
    val seatsLeft: Int,
    val feeAmount: Double = 0.0,
    val status: String = "published",
    val distanceKm: Double? = null,
    val travelEstimateMinutes: Int? = null,
    val isDemo: Boolean = false,
    val collegeName: String = college.name,
    val formattedDate: String = "",
    val formattedTime: String = "",
    val description: String = "",
    val minTeamSize: Int = teamMin,
    val maxTeamSize: Int = teamMax,
    val isFree: Boolean = feeAmount <= 0.0,
    val smartLeaveBy: String? = null,
    val smartTravelMinutes: Int? = travelEstimateMinutes,
    val venueAddress: String = venueName,
    val ticketToken: String = "",
    val ticketCode: String = ""
)
