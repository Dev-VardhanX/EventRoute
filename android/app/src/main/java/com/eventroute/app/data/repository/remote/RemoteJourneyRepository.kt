package com.eventroute.app.data.repository.remote

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.api.JourneyApi
import com.eventroute.app.data.remote.dto.SelectJourneyPlanRequest
import com.eventroute.app.domain.model.Journey
import com.eventroute.app.domain.model.Route
import com.eventroute.app.domain.repository.JourneyRepository

class RemoteJourneyRepository(
    private val journeyApi: JourneyApi
) : JourneyRepository {

    override suspend fun getJourneyPlan(eventId: String, originLat: Double, originLng: Double): Result<Journey> = runCatching {
        val response = journeyApi.getEventJourney(eventId, originLat, originLng)
        response.toDomain()
    }

    override suspend fun selectJourneyPlan(planId: String, routeId: String?): Result<Unit> = runCatching {
        journeyApi.selectJourneyPlan(planId, SelectJourneyPlanRequest(planId, routeId))
        Unit
    }

    override suspend fun getRoute(origin: String, destination: String, mode: String): Result<Route> = runCatching {
        val dto = journeyApi.getRoute(origin, destination, mode)
        dto.toDomain()
    }
}
