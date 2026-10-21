package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.RefreshRequest
import com.eventroute.app.data.remote.dto.TokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthRefreshApi {
    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse
}
