package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.Team
import com.eventroute.app.domain.model.TeamPlan

interface TeamsRepository {
    suspend fun createTeam(eventId: String, teamName: String): Result<Team>
    suspend fun joinTeam(inviteCode: String): Result<Team>
    suspend fun getTeam(teamId: String): Result<Team>
    suspend fun confirmTeam(teamId: String): Result<Team>
    suspend fun updateStartPoint(teamId: String, lat: Double, lng: Double, address: String): Result<Unit>
    suspend fun setMyStartPoint(teamId: String, lat: Double, lng: Double, label: String, pref: String = "Fastest"): Result<TeamPlan>
    suspend fun getTeamPlan(teamId: String): Result<TeamPlan>
    suspend fun respondSuggestion(teamId: String, suggestionId: String, accept: Boolean): Result<Unit>
}
