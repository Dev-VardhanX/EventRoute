package com.eventroute.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    @SerialName("college_id") val collegeId: String
)

@Serializable
data class VerifyEmailRequest(
    val email: String,
    val code: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val user: UserDto? = null
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "student",
    @SerialName("college_id") val collegeId: String? = null,
    @SerialName("college_name") val collegeName: String? = null,
    val interests: List<String> = emptyList(),
    @SerialName("home_lat") val homeLat: Double? = null,
    @SerialName("home_lng") val homeLng: Double? = null,
    @SerialName("home_label") val homeLabel: String? = null,
    @SerialName("default_start") val defaultStart: String = "college",
    @SerialName("is_email_verified") val isEmailVerified: Boolean = false
)

@Serializable
data class StartPointUpdateRequest(
    @SerialName("default_start") val defaultStart: String,
    val lat: Double,
    val lng: Double,
    val label: String
)

@Serializable
data class DeviceTokenRequest(
    @SerialName("fcm_token") val fcmToken: String
)
