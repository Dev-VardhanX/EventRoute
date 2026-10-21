package com.eventroute.app.data.repository.mock

import android.content.Context
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.mock.AssetReader
import com.eventroute.app.data.remote.dto.EventListResponse
import com.eventroute.app.domain.live.LiveFeedSimulator
import com.eventroute.app.domain.model.*
import com.eventroute.app.domain.repository.EventsRepository
import kotlinx.coroutines.delay

class MockEventsRepository(
    private val context: Context
) : EventsRepository {

    override suspend fun getEvents(
        category: String?,
        searchQuery: String?,
        participation: String?
    ): Result<List<Event>> {
        val query = EventQuery(
            text = searchQuery.orEmpty(),
            category = category ?: "All Events",
            participation = when (participation?.lowercase()) {
                "solo" -> ParticipationFilter.SOLO
                "team" -> ParticipationFilter.TEAM
                "either" -> ParticipationFilter.EITHER
                else -> ParticipationFilter.ALL
            }
        )
        return queryEvents(query)
    }

    override suspend fun queryEvents(query: EventQuery): Result<List<Event>> {
        return runCatching {
            com.eventroute.app.debug.DebugFlags.checkAndSimulate("events")
            delay(150)
            val response: EventListResponse = AssetReader.readJsonAsset(context, "mock/events.json")
            var items = response.items.map { it.toDomain() }

            // Category filter
            if (query.category.isNotBlank() && !query.category.equals("All", true) && !query.category.equals("All Events", true)) {
                items = items.filter { it.category.contains(query.category.removeSuffix("s"), ignoreCase = true) }
            }

            // Participation filter
            when (query.participation) {
                ParticipationFilter.SOLO -> items = items.filter { it.participationType.equals("solo", true) }
                ParticipationFilter.TEAM -> items = items.filter { it.participationType.equals("team", true) }
                ParticipationFilter.EITHER -> items = items.filter { it.participationType.equals("either", true) }
                ParticipationFilter.ALL -> { /* no filter */ }
            }

            // Free / Paid filter
            when (query.feeFilter) {
                FeeFilter.FREE -> items = items.filter { it.isFree }
                FeeFilter.PAID -> items = items.filter { !it.isFree }
                FeeFilter.ALL -> { /* no filter */ }
            }

            // Search text
            if (query.text.isNotBlank()) {
                val q = query.text.trim().lowercase()
                items = items.filter {
                    it.title.lowercase().contains(q) ||
                    it.collegeName.lowercase().contains(q) ||
                    it.venueName.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
                }
            }

            // Date Range
            if (query.startDate != null) {
                items = items.filter { it.startAt >= query.startDate }
            }
            if (query.endDate != null) {
                items = items.filter { it.startAt <= query.endDate }
            }

            // Distance calculation and filter
            if (query.userLat != null && query.userLng != null) {
                val userPoint = GeoPoint(query.userLat, query.userLng)
                items = items.map { event ->
                    val distKm = LiveFeedSimulator.haversineM(userPoint, GeoPoint(event.venueLat, event.venueLng)) / 1000.0
                    event.copy(distanceKm = Math.round(distKm * 10.0) / 10.0)
                }
                if (query.maxDistanceKm != null) {
                    items = items.filter { (it.distanceKm ?: Double.MAX_VALUE) <= query.maxDistanceKm }
                }
            }

            // Max travel minutes
            if (query.maxTravelMin != null) {
                items = items.filter {
                    val travelMin = it.smartTravelMinutes ?: it.travelEstimateMinutes ?: 0
                    travelMin <= query.maxTravelMin
                }
            }

            // Sorting
            items = when (query.sort) {
                SortOrder.SOONEST -> items.sortedBy { it.startAt }
                SortOrder.NEAREST -> items.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
                SortOrder.SHORTEST_TRAVEL_TIME -> items.sortedBy { it.smartTravelMinutes ?: it.travelEstimateMinutes ?: Int.MAX_VALUE }
            }

            items
        }
    }

    override suspend fun getEventById(id: String): Result<Event> {
        return runCatching {
            com.eventroute.app.debug.DebugFlags.checkAndSimulate("events")
            delay(150)
            val response: EventListResponse = AssetReader.readJsonAsset(context, "mock/events.json")
            response.items.find { it.id == id }?.toDomain()
                ?: throw NoSuchElementException("Event not found with id: $id")
        }
    }

    override suspend fun getTravelEstimates(
        originLat: Double,
        originLng: Double,
        eventIds: List<String>
    ): Result<Map<String, Int>> {
        delay(100)
        return runCatching {
            eventIds.associateWith { 35 }
        }
    }
}
