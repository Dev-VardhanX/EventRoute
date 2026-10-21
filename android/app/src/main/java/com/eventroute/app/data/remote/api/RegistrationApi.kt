package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.RegistrationDto
import com.eventroute.app.data.remote.dto.RegistrationListResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

interface RegistrationApi {
    @POST("events/{id}/register")
    suspend fun registerSolo(@Path("id") eventId: String): RegistrationDto

    @DELETE("registrations/{id}")
    suspend fun cancelRegistration(@Path("id") registrationId: String): Map<String, Boolean>

    @GET("me/registrations")
    suspend fun getMyRegistrations(): RegistrationListResponse
}
