package com.eventroute.app.data.repository.remote

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.TokenStore
import com.eventroute.app.data.remote.api.AuthApi
import com.eventroute.app.data.remote.api.MeApi
import com.eventroute.app.data.remote.dto.LoginRequest
import com.eventroute.app.data.remote.dto.RegisterRequest
import com.eventroute.app.data.remote.dto.VerifyEmailRequest
import com.eventroute.app.domain.model.User
import com.eventroute.app.domain.repository.AuthRepository

class RemoteAuthRepository(
    private val authApi: AuthApi,
    private val meApi: MeApi,
    private val tokenStore: TokenStore
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val response = authApi.login(LoginRequest(email, password))
        tokenStore.save(response.accessToken, response.refreshToken)
        response.user?.toDomain() ?: meApi.getMe().toDomain()
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        collegeId: String
    ): Result<User> = runCatching {
        val response = authApi.register(RegisterRequest(name, email, password, collegeId))
        tokenStore.save(response.accessToken, response.refreshToken)
        response.user?.toDomain() ?: meApi.getMe().toDomain()
    }

    override suspend fun verifyEmail(email: String, code: String): Result<Boolean> = runCatching {
        val response = authApi.verifyEmail(VerifyEmailRequest(email, code))
        response["success"] ?: true
    }

    override suspend fun getCurrentUser(): Result<User?> = runCatching {
        if (tokenStore.access == null) return@runCatching null
        meApi.getMe().toDomain()
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        runCatching { authApi.logout() }
        tokenStore.clear()
        Unit
    }
}
