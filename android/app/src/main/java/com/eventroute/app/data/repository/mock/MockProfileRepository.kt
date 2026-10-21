package com.eventroute.app.data.repository.mock

import android.content.Context
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.mock.AssetReader
import com.eventroute.app.data.remote.dto.CollegeDto
import com.eventroute.app.data.remote.dto.UserDto
import com.eventroute.app.domain.model.College
import com.eventroute.app.domain.model.User
import com.eventroute.app.domain.repository.ProfileRepository
import kotlinx.coroutines.delay

class MockProfileRepository(
    private val context: Context
) : ProfileRepository {

    private var currentUser: User? = null

    private fun loadUser(): User {
        if (currentUser == null) {
            val userDto: UserDto = AssetReader.readJsonAsset(context, "mock/me.json")
            currentUser = userDto.toDomain()
        }
        return currentUser!!
    }

    override suspend fun getProfile(): Result<User> {
        delay(250)
        return Result.success(loadUser())
    }

    override suspend fun updateStartPoint(
        defaultStart: String,
        lat: Double,
        lng: Double,
        label: String
    ): Result<User> {
        delay(250)
        val updated = loadUser().copy(
            defaultStart = defaultStart,
            homeLat = lat,
            homeLng = lng,
            homeLabel = label
        )
        currentUser = updated
        return Result.success(updated)
    }

    override suspend fun registerDeviceToken(token: String): Result<Unit> {
        delay(150)
        return Result.success(Unit)
    }

    override suspend fun getColleges(search: String?): Result<List<College>> {
        delay(200)
        return runCatching {
            val list: List<CollegeDto> = AssetReader.readJsonAsset(context, "mock/colleges.json")
            var colleges = list.map { it.toDomain() }
            if (!search.isNullOrBlank()) {
                colleges = colleges.filter { it.name.contains(search, ignoreCase = true) }
            }
            colleges
        }
    }
}
