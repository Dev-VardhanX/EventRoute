package com.eventroute.app.data.remote

import com.eventroute.app.data.remote.dto.*

/**
 * EventRoute Remote API definition.
 * Aligned with OpenAPI specifications at /api/v1
 */
interface EventApi {
    suspend fun getEvents(
        category: String? = null,
        participation: String? = null,
        searchQuery: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): EventListResponse

    suspend fun getEventById(id: String): EventDto

    suspend fun getTravelEstimates(request: TravelEstimateRequest): TravelEstimateResponse

    suspend fun registerSolo(eventId: String): Map<String, Any>

    suspend fun createTeam(eventId: String, request: CreateTeamRequest): TeamDto

    suspend fun joinTeam(request: JoinTeamRequest): TeamDto

    suspend fun getTeam(teamId: String): TeamDto

    suspend fun confirmTeam(teamId: String): TeamDto

    suspend fun getTeamPlan(teamId: String): TeamPlanDto

    suspend fun getJourneyPlan(eventId: String, lat: Double, lng: Double): JourneyDto

    suspend fun login(request: LoginRequest): TokenResponse

    suspend fun register(request: RegisterRequest): TokenResponse

    suspend fun verifyEmail(request: VerifyEmailRequest): Map<String, Any>

    suspend fun getCurrentUser(): UserDto
}
