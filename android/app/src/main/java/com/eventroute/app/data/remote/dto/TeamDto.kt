package com.eventroute.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateTeamRequest(
    val name: String
)

@Serializable
data class JoinTeamRequest(
    @SerialName("invite_code") val inviteCode: String
)

@Serializable
data class TeamMemberDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("college_name") val collegeName: String? = null,
    @SerialName("is_leader") val isLeader: Boolean = false,
    @SerialName("start_lat") val startLat: Double? = null,
    @SerialName("start_lng") val startLng: Double? = null,
    @SerialName("start_address") val startAddress: String? = null,
    @SerialName("travel_pref") val travelPref: String = "transit",
    @SerialName("planned_leave_by") val plannedLeaveBy: String? = null,
    @SerialName("planned_arrival_at") val plannedArrivalAt: String? = null,
    @SerialName("journey_status") val journeyStatus: String = "idle",
    @SerialName("live_lat") val liveLat: Double? = null,
    @SerialName("live_lng") val liveLng: Double? = null,
    @SerialName("live_eta_at") val liveEtaAt: String? = null
)

@Serializable
data class TeamDto(
    val id: String,
    @SerialName("event_id") val eventId: String,
    val name: String,
    @SerialName("leader_id") val leaderId: String,
    @SerialName("invite_code") val inviteCode: String,
    val status: String = "forming",
    @SerialName("planned_arrival_at") val plannedArrivalAt: String? = null,
    @SerialName("plan_version") val planVersion: Int = 1,
    val members: List<TeamMemberDto> = emptyList()
)

@Serializable
data class JoinSuggestionDto(
    val id: String,
    val type: String,
    val summary: String,
    @SerialName("member_ids") val memberIds: List<String> = emptyList(),
    @SerialName("meet_point") val meetPoint: PlaceDto? = null,
    @SerialName("meet_at") val meetAt: String? = null,
    @SerialName("extra_minutes") val extraMinutes: Int = 0,
    val status: String = "pending"
)

@Serializable
data class TeamPlanResponse(
    @SerialName("team_id") val teamId: String,
    @SerialName("target_arrival_at") val targetArrivalAt: String,
    @SerialName("arrival_spread_minutes") val arrivalSpreadMinutes: Int,
    @SerialName("is_at_risk") val isAtRisk: Boolean = false,
    @SerialName("at_risk_reason") val atRiskReason: String? = null,
    val members: List<TeamMemberDto>,
    @SerialName("travel_together_suggestion") val travelTogetherSuggestion: String? = null,
    val suggestions: List<JoinSuggestionDto> = emptyList(),
    @SerialName("plan_version") val planVersion: Int = 1
)

typealias TeamPlanDto = TeamPlanResponse

@Serializable
data class UpdateStartPointRequest(
    val lat: Double,
    val lng: Double,
    val address: String,
    @SerialName("travel_pref") val travelPref: String = "transit"
)

@Serializable
data class RespondSuggestionRequest(
    val accept: Boolean
)
