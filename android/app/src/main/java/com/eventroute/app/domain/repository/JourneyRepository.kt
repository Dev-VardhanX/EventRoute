package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.Journey
import com.eventroute.app.domain.model.Route

interface JourneyRepository {
    suspend fun getJourneyPlan(eventId: String, originLat: Double, originLng: Double): Result<Journey>
    suspend fun selectJourneyPlan(planId: String, routeId: String? = null): Result<Unit>
    suspend fun getRoute(origin: String, destination: String, mode: String = "transit"): Result<Route>
}
