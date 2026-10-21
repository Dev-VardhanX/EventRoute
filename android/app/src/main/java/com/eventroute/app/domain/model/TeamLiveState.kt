package com.eventroute.app.domain.model

data class TeamLiveState(
    val teamId: String,
    val members: List<TeamMember>,
    val alertMessage: String? = null,
    val targetArrivalAt: String = "09:30 AM",
    val venueLocation: GeoPoint = GeoPoint(28.5459, 77.1926),
    val venueName: String = "IIT Delhi"
)
