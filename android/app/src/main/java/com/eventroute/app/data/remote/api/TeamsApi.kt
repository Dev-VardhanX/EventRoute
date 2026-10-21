package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.*
import retrofit2.http.*

interface TeamsApi {
    @POST("events/{id}/teams")
    suspend fun createTeam(
        @Path("id") eventId: String,
        @Body request: CreateTeamRequest
    ): TeamDto

    @GET("invite/{code}")
    suspend fun getTeamByInviteCode(@Path("code") code: String): TeamDto

    @POST("teams/join")
    suspend fun joinTeam(@Body request: JoinTeamRequest): TeamDto

    @GET("teams/{id}")
    suspend fun getTeam(@Path("id") teamId: String): TeamDto

    @POST("teams/{id}/confirm")
    suspend fun confirmTeam(@Path("id") teamId: String): TeamDto

    @PUT("teams/{id}/me/start-point")
    suspend fun updateStartPoint(
        @Path("id") teamId: String,
        @Body request: UpdateStartPointRequest
    ): TeamMemberDto

    @DELETE("teams/{id}/members/{user_id}")
    suspend fun removeMember(
        @Path("id") teamId: String,
        @Path("user_id") userId: String
    ): Map<String, Boolean>

    @GET("teams/{id}/plan")
    suspend fun getTeamPlan(@Path("id") teamId: String): TeamPlanDto

    @POST("teams/{id}/suggestions/{sid}/respond")
    suspend fun respondSuggestion(
        @Path("id") teamId: String,
        @Path("sid") suggestionId: String,
        @Body request: RespondSuggestionRequest
    ): Map<String, Boolean>
}

