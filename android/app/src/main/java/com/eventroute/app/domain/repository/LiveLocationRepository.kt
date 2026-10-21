package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.GeoPoint

interface LiveLocationRepository {
    suspend fun publishLocation(teamId: String, location: GeoPoint): Result<Unit>
}
