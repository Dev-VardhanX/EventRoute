package com.eventroute.app.data.repository.mock

import android.content.Context
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.mock.AssetReader
import com.eventroute.app.data.remote.TokenStore
import com.eventroute.app.data.remote.dto.UserDto
import com.eventroute.app.domain.model.User
import com.eventroute.app.domain.repository.AuthRepository
import kotlinx.coroutines.delay

class MockAuthRepository(
    private val context: Context,
    private val tokenStore: TokenStore
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        delay(300)
        return runCatching {
            tokenStore.save("mock_access_token_${System.currentTimeMillis()}", "mock_refresh_token")
            val userDto: UserDto = AssetReader.readJsonAsset(context, "mock/me.json")
            userDto.toDomain().copy(email = email)
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        collegeId: String
    ): Result<User> {
        delay(300)
        return runCatching {
            tokenStore.save("mock_access_token_${System.currentTimeMillis()}", "mock_refresh_token")
            val userDto: UserDto = AssetReader.readJsonAsset(context, "mock/me.json")
            userDto.toDomain().copy(name = name, email = email, collegeId = collegeId)
        }
    }

    override suspend fun verifyEmail(email: String, code: String): Result<Boolean> {
        delay(200)
        return Result.success(code.isNotBlank())
    }

    override suspend fun getCurrentUser(): Result<User?> {
        delay(150)
        return runCatching {
            val userDto: UserDto = AssetReader.readJsonAsset(context, "mock/me.json")
            userDto.toDomain()
        }
    }

    override suspend fun logout(): Result<Unit> {
        delay(150)
        tokenStore.clear()
        return Result.success(Unit)
    }
}
