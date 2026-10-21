package com.eventroute.app.data.repository.remote

import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.domain.repository.LiveLocationRepository

/**
 * Remote stub for POST /teams/{id}/location.
 */
class RemoteLiveLocationRepository : LiveLocationRepository {

    override suspend fun publishLocation(teamId: String, location: GeoPoint): Result<Unit> {
        // Future backend implementation: call TeamsApi.postLocation(teamId, LocationDto(lat, lng))
        return Result.success(Unit)
    }
}
