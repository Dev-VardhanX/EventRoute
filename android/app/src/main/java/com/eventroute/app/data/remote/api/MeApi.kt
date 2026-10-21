package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

interface MeApi {
    @GET("me")
    suspend fun getMe(): UserDto

    @PATCH("me")
    suspend fun updateMe(@Body updates: Map<String, String>): UserDto

    @PUT("me/start-point")
    suspend fun updateStartPoint(@Body request: StartPointUpdateRequest): UserDto

    @POST("me/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): Map<String, Boolean>
}
