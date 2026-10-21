package com.eventroute.app.domain.model

data class TeamMember(
    val userId: String,
    val name: String,
    val collegeName: String = "",
    val isLeader: Boolean = false,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val startAddress: String = "",
    val travelPref: String = "transit", // "transit", "cab", "metro", "personal"
    val plannedLeaveBy: String? = null,
    val plannedArrivalAt: String? = null,
    val journeyStatus: String = "idle", // "idle", "traveling", "arrived", "delayed"
    val liveLat: Double? = null,
    val liveLng: Double? = null,
    val liveEtaAt: String? = null
)

data class JoinSuggestion(
    val id: String,
    val type: String,
    val summary: String,
    val extraMinutes: Int = 0,
    val status: String = "pending"
)

data class TeamPlan(
    val teamId: String,
    val targetArrivalAt: String,
    val arrivalSpreadMinutes: Int,
    val isAtRisk: Boolean = false,
    val atRiskReason: String? = null,
    val members: List<TeamMember> = emptyList(),
    val travelTogetherSuggestion: String? = null,
    val suggestions: List<JoinSuggestion> = emptyList(),
    val planVersion: Int = 1
)

data class Team(
    val id: String,
    val eventId: String,
    val name: String,
    val leaderId: String,
    val inviteCode: String,
    val status: String = "forming", // "forming", "confirmed", "cancelled"
    val plannedArrivalAt: String? = null,
    val planVersion: Int = 1,
    val members: List<TeamMember> = emptyList(),
    val plan: TeamPlan? = null
)
