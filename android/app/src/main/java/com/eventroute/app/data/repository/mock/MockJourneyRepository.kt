package com.eventroute.app.data.repository.mock

import android.content.Context
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.mock.AssetReader
import com.eventroute.app.data.remote.dto.JourneyPlanResponse
import com.eventroute.app.domain.model.Journey
import com.eventroute.app.domain.model.Route
import com.eventroute.app.domain.repository.JourneyRepository
import kotlinx.coroutines.delay

class MockJourneyRepository(
    private val context: Context
) : JourneyRepository {

    override suspend fun getJourneyPlan(eventId: String, originLat: Double, originLng: Double): Result<Journey> {
        return runCatching {
            com.eventroute.app.debug.DebugFlags.checkAndSimulate("journey")
            delay(300)
            val response: JourneyPlanResponse = AssetReader.readJsonAsset(context, "mock/journey.json")
            response.toDomain().copy(
                eventId = eventId,
                originLat = originLat,
                originLng = originLng
            )
        }
    }

    override suspend fun selectJourneyPlan(planId: String, routeId: String?): Result<Unit> {
        delay(200)
        return Result.success(Unit)
    }

    override suspend fun getRoute(origin: String, destination: String, mode: String): Result<Route> {
        delay(300)
        return runCatching {
            val response: JourneyPlanResponse = AssetReader.readJsonAsset(context, "mock/journey.json")
            response.routes.first().toDomain()
        }
    }
}
