package com.eventroute.app.data.repository.remote

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.api.CollegesApi
import com.eventroute.app.data.remote.api.MeApi
import com.eventroute.app.data.remote.dto.DeviceTokenRequest
import com.eventroute.app.data.remote.dto.StartPointUpdateRequest
import com.eventroute.app.domain.model.College
import com.eventroute.app.domain.model.User
import com.eventroute.app.domain.repository.ProfileRepository

class RemoteProfileRepository(
    private val meApi: MeApi,
    private val collegesApi: CollegesApi
) : ProfileRepository {

    override suspend fun getProfile(): Result<User> = runCatching {
        val dto = meApi.getMe()
        dto.toDomain()
    }

    override suspend fun updateStartPoint(
        defaultStart: String,
        lat: Double,
        lng: Double,
        label: String
    ): Result<User> = runCatching {
        val dto = meApi.updateStartPoint(StartPointUpdateRequest(defaultStart, lat, lng, label))
        dto.toDomain()
    }

    override suspend fun registerDeviceToken(token: String): Result<Unit> = runCatching {
        meApi.registerDeviceToken(DeviceTokenRequest(token))
        Unit
    }

    override suspend fun getColleges(search: String?): Result<List<College>> = runCatching {
        val list = collegesApi.getColleges(search)
        list.map { it.toDomain() }
    }
}
