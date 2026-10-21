package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.*
import retrofit2.http.*

interface JourneyApi {
    @GET("route")
    suspend fun getRoute(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "transit",
        @Query("arrive_by") arriveBy: String? = null
    ): RouteDto

    @GET("events/{id}/journey")
    suspend fun getEventJourney(
        @Path("id") eventId: String,
        @Query("origin_lat") originLat: Double? = null,
        @Query("origin_lng") originLng: Double? = null
    ): JourneyPlanResponse

    @POST("journey/{plan_id}/select")
    suspend fun selectJourneyPlan(
        @Path("plan_id") planId: String,
        @Body request: SelectJourneyPlanRequest
    ): Map<String, Boolean>

    @GET("teams/{id}/plan")
    suspend fun getTeamPlan(@Path("id") teamId: String): TeamPlanResponse

    @POST("teams/{id}/suggestions/{sid}/respond")
    suspend fun respondSuggestion(
        @Path("id") teamId: String,
        @Path("sid") suggestionId: String,
        @Body request: RespondSuggestionRequest
    ): Map<String, Boolean>

    @POST("teams/{id}/journey/start")
    suspend fun startJourney(@Path("id") teamId: String): Map<String, Boolean>

    @POST("teams/{id}/journey/stop")
    suspend fun stopJourney(@Path("id") teamId: String): Map<String, Boolean>

    @GET("teams/{id}/live")
    suspend fun getLiveTeamBoard(@Path("id") teamId: String): TeamPlanResponse
}
