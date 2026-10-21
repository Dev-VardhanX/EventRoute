package com.eventroute.app.data.repository.remote

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.api.TeamsApi
import com.eventroute.app.data.remote.dto.CreateTeamRequest
import com.eventroute.app.data.remote.dto.JoinTeamRequest
import com.eventroute.app.data.remote.dto.RespondSuggestionRequest
import com.eventroute.app.data.remote.dto.UpdateStartPointRequest
import com.eventroute.app.domain.model.Team
import com.eventroute.app.domain.model.TeamPlan
import com.eventroute.app.domain.repository.TeamsRepository

class RemoteTeamsRepository(
    private val teamsApi: TeamsApi
) : TeamsRepository {

    override suspend fun createTeam(eventId: String, teamName: String): Result<Team> = runCatching {
        val dto = teamsApi.createTeam(eventId, CreateTeamRequest(teamName))
        dto.toDomain()
    }

    override suspend fun joinTeam(inviteCode: String): Result<Team> = runCatching {
        val dto = teamsApi.joinTeam(JoinTeamRequest(inviteCode))
        dto.toDomain()
    }

    override suspend fun getTeam(teamId: String): Result<Team> = runCatching {
        val dto = teamsApi.getTeam(teamId)
        dto.toDomain()
    }

    override suspend fun confirmTeam(teamId: String): Result<Team> = runCatching {
        val dto = teamsApi.confirmTeam(teamId)
        dto.toDomain()
    }

    override suspend fun updateStartPoint(
        teamId: String,
        lat: Double,
        lng: Double,
        address: String
    ): Result<Unit> = runCatching {
        teamsApi.updateStartPoint(teamId, UpdateStartPointRequest(lat, lng, address))
        Unit
    }

    override suspend fun setMyStartPoint(
        teamId: String,
        lat: Double,
        lng: Double,
        label: String,
        pref: String
    ): Result<TeamPlan> = runCatching {
        teamsApi.updateStartPoint(teamId, UpdateStartPointRequest(lat, lng, label, pref))
        getTeamPlan(teamId).getOrThrow()
    }

    override suspend fun getTeamPlan(teamId: String): Result<TeamPlan> = runCatching {
        val dto = teamsApi.getTeamPlan(teamId)
        dto.toDomain()
    }

    override suspend fun respondSuggestion(
        teamId: String,
        suggestionId: String,
        accept: Boolean
    ): Result<Unit> = runCatching {
        teamsApi.respondSuggestion(teamId, suggestionId, RespondSuggestionRequest(accept))
        Unit
    }
}
