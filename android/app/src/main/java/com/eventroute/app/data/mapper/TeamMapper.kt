package com.eventroute.app.data.mapper

import com.eventroute.app.data.remote.dto.*
import com.eventroute.app.domain.model.*

fun TeamMemberDto.toDomain(): TeamMember = TeamMember(
    userId = userId,
    name = name,
    collegeName = collegeName.orEmpty(),
    isLeader = isLeader,
    startLat = startLat,
    startLng = startLng,
    startAddress = startAddress.orEmpty(),
    travelPref = travelPref,
    plannedLeaveBy = plannedLeaveBy,
    plannedArrivalAt = plannedArrivalAt,
    journeyStatus = journeyStatus,
    liveLat = liveLat,
    liveLng = liveLng,
    liveEtaAt = liveEtaAt
)

fun JoinSuggestionDto.toDomain(): JoinSuggestion = JoinSuggestion(
    id = id,
    type = type,
    summary = summary,
    extraMinutes = extraMinutes,
    status = status
)

fun TeamPlanResponse.toDomain(): TeamPlan = TeamPlan(
    teamId = teamId,
    targetArrivalAt = targetArrivalAt,
    arrivalSpreadMinutes = arrivalSpreadMinutes,
    isAtRisk = isAtRisk,
    atRiskReason = atRiskReason,
    members = members.map { it.toDomain() },
    travelTogetherSuggestion = travelTogetherSuggestion,
    suggestions = suggestions.map { it.toDomain() },
    planVersion = planVersion
)

fun TeamDto.toDomain(plan: TeamPlan? = null): Team = Team(
    id = id,
    eventId = eventId,
    name = name,
    leaderId = leaderId,
    inviteCode = inviteCode,
    status = status,
    plannedArrivalAt = plannedArrivalAt,
    planVersion = planVersion,
    members = members.map { it.toDomain() },
    plan = plan
)
