package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface EventsApi {
    @GET("events")
    suspend fun getEvents(
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius_km") radiusKm: Double? = null,
        @Query("category") category: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("participation") participation: String? = null,
        @Query("max_travel_min") maxTravelMin: Int? = null,
        @Query("q") query: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): EventListResponse

    @GET("events/{id}")
    suspend fun getEventById(@Path("id") id: String): EventDto

    @POST("events/travel-estimates")
    suspend fun getTravelEstimates(@Body request: TravelEstimateRequest): TravelEstimateResponse
}
