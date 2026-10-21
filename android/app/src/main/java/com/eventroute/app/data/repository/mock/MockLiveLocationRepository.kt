package com.eventroute.app.data.repository.mock

import com.eventroute.app.domain.live.LiveFeedSimulator
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.domain.repository.LiveLocationRepository

class MockLiveLocationRepository : LiveLocationRepository {

    override suspend fun publishLocation(teamId: String, location: GeoPoint): Result<Unit> {
        LiveFeedSimulator.updateUserLocation(location)
        return Result.success(Unit)
    }
}
