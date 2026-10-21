package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): Map<String, Boolean>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(): Map<String, Boolean>
}
