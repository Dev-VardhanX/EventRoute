package com.eventroute.app.data.repository.mock

import android.content.Context
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.mock.AssetReader
import com.eventroute.app.data.remote.ApiException
import com.eventroute.app.data.remote.dto.TeamDto
import com.eventroute.app.data.remote.dto.TeamPlanResponse
import com.eventroute.app.domain.model.Team
import com.eventroute.app.domain.model.TeamMember
import com.eventroute.app.domain.model.TeamPlan
import com.eventroute.app.domain.repository.TeamsRepository
import kotlinx.coroutines.delay

class MockTeamsRepository(
    private val context: Context
) : TeamsRepository {

    private var currentTeam: Team? = null
    private var currentPlan: TeamPlan? = null

    private fun ensureLoaded() {
        if (currentTeam == null) {
            val teamDto: TeamDto = AssetReader.readJsonAsset(context, "mock/team.json")
            val planDto: TeamPlanResponse = AssetReader.readJsonAsset(context, "mock/team_plan.json")
            currentPlan = planDto.toDomain()
            currentTeam = teamDto.toDomain(currentPlan)
        }
    }

    override suspend fun createTeam(eventId: String, teamName: String): Result<Team> {
        return runCatching {
            com.eventroute.app.debug.DebugFlags.checkAndSimulate("teams")
            delay(300)
            val newTeam = Team(
                id = "team-${System.currentTimeMillis()}",
                eventId = eventId,
                name = teamName,
                leaderId = "u-john",
                inviteCode = "TEAM" + (1000..9999).random(),
                status = "forming",
                members = listOf(
                    TeamMember(
                        userId = "u-john",
                        name = "John Doe",
                        collegeName = "NSUT, Dwarka",
                        isLeader = true,
                        travelPref = "transit"
                    )
                )
            )
            currentTeam = newTeam
            newTeam
        }
    }

    override suspend fun joinTeam(inviteCode: String): Result<Team> {
        return runCatching {
            com.eventroute.app.debug.DebugFlags.checkAndSimulate("teams")
            delay(300)
            ensureLoaded()
            val team = currentTeam ?: throw ApiException("NOT_FOUND", "Team not found", 404)
            if (inviteCode.equals(team.inviteCode, ignoreCase = true) || inviteCode.equals("HACK-291X", ignoreCase = true)) {
                team
            } else {
                throw ApiException("INVITE_INVALID", "That invite code is not valid.", 400)
            }
        }
    }

    override suspend fun getTeam(teamId: String): Result<Team> {
        return runCatching {
            com.eventroute.app.debug.DebugFlags.checkAndSimulate("teams")
            delay(300)
            ensureLoaded()
            currentTeam ?: throw ApiException("NOT_FOUND", "Team not found", 404)
        }
    }

    override suspend fun confirmTeam(teamId: String): Result<Team> {
        delay(300)
        ensureLoaded()
        val updated = currentTeam?.copy(status = "confirmed") ?: return Result.failure(ApiException("NOT_FOUND", "Team not found", 404))
        currentTeam = updated
        return Result.success(updated)
    }

    private var planVersionCounter = 1
    private val suggestionResponses = mutableMapOf<String, Boolean>()

    private data class MockPlanVariant(
        val leaveBy: String,
        val spreadMinutes: Int,
        val isAtRisk: Boolean,
        val atRiskReason: String?
    )

    override suspend fun updateStartPoint(
        teamId: String,
        lat: Double,
        lng: Double,
        address: String
    ): Result<Unit> {
        return setMyStartPoint(teamId, lat, lng, address, "Fastest").map { Unit }
    }

    override suspend fun setMyStartPoint(
        teamId: String,
        lat: Double,
        lng: Double,
        label: String,
        pref: String
    ): Result<TeamPlan> {
        delay(300)
        ensureLoaded()
        planVersionCounter++

        // ── Mock Behavior: canned plan variants based on start point area ──
        val isRohiniOrNorth = lat > 28.7 || label.contains("Rohini", ignoreCase = true) || label.contains("DTU", ignoreCase = true)
        val isEastOrFar = lng > 77.3 || label.contains("Noida", ignoreCase = true) || label.contains("East", ignoreCase = true)
        val isNearIIT = (lat in 28.52..28.56) && (lng in 77.18..77.22)

        val basePlan = currentPlan ?: return Result.failure(ApiException("NOT_FOUND", "Team plan not found", 404))

        val variant = when {
            isEastOrFar -> MockPlanVariant("08:05 AM", 24, true, "Blue Line peak congestion")
            isRohiniOrNorth -> MockPlanVariant("08:20 AM", 6, false, null)
            isNearIIT -> MockPlanVariant("09:05 AM", 5, false, null)
            else -> MockPlanVariant(
                if (pref.equals("Cheapest", true)) "08:35 AM" else "08:45 AM",
                if (pref.equals("Fewest transfers", true)) 8 else 12,
                false,
                null
            )
        }

        val updatedMembers = basePlan.members.map { m ->
            if (m.userId == "u-john" || m.isLeader) {
                m.copy(
                    startLat = lat,
                    startLng = lng,
                    startAddress = label,
                    travelPref = pref.lowercase(),
                    plannedLeaveBy = variant.leaveBy
                )
            } else m
        }

        val updatedPlan = basePlan.copy(
            arrivalSpreadMinutes = variant.spreadMinutes,
            isAtRisk = variant.isAtRisk,
            atRiskReason = variant.atRiskReason,
            members = updatedMembers,
            planVersion = planVersionCounter
        )
        currentPlan = updatedPlan
        currentTeam = currentTeam?.copy(
            planVersion = planVersionCounter,
            members = updatedMembers,
            plan = updatedPlan
        )
        return Result.success(updatedPlan)
    }

    override suspend fun getTeamPlan(teamId: String): Result<TeamPlan> {
        delay(300)
        ensureLoaded()
        return Result.success(currentPlan ?: return Result.failure(ApiException("NOT_FOUND", "Team plan not found", 404)))
    }

    override suspend fun respondSuggestion(
        teamId: String,
        suggestionId: String,
        accept: Boolean
    ): Result<Unit> {
        delay(200)
        ensureLoaded()
        suggestionResponses[suggestionId] = accept

        currentPlan = currentPlan?.let { plan ->
            val updated = plan.suggestions.map { sug ->
                if (sug.id == suggestionId) {
                    sug.copy(status = if (accept) "accepted" else "declined")
                } else sug
            }
            plan.copy(suggestions = updated)
        }
        return Result.success(Unit)
    }
}
