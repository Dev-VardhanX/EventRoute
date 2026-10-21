package com.eventroute.app.data.repository.remote

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.api.EventsApi
import com.eventroute.app.data.remote.dto.TravelEstimateRequest
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.model.EventQuery
import com.eventroute.app.domain.model.ParticipationFilter
import com.eventroute.app.domain.model.SortOrder
import com.eventroute.app.domain.repository.EventsRepository

class RemoteEventsRepository(
    private val eventsApi: EventsApi
) : EventsRepository {

    override suspend fun getEvents(
        category: String?,
        searchQuery: String?,
        participation: String?
    ): Result<List<Event>> = runCatching {
        val cleanCategory = if (category.equals("All", ignoreCase = true) || category.equals("All Events", ignoreCase = true)) null else category
        val response = eventsApi.getEvents(
            category = cleanCategory,
            query = searchQuery,
            participation = participation
        )
        response.items.map { it.toDomain() }
    }

    override suspend fun queryEvents(query: EventQuery): Result<List<Event>> = runCatching {
        val cleanCategory = if (query.category.startsWith("All", true)) null else query.category
        val participationStr = when (query.participation) {
            ParticipationFilter.SOLO -> "solo"
            ParticipationFilter.TEAM -> "team"
            ParticipationFilter.EITHER -> "either"
            ParticipationFilter.ALL -> null
        }
        val response = eventsApi.getEvents(
            category = cleanCategory,
            query = query.text.ifBlank { null },
            participation = participationStr
        )
        response.items.map { it.toDomain() }
    }

    override suspend fun getEventById(id: String): Result<Event> = runCatching {
        val dto = eventsApi.getEventById(id)
        dto.toDomain()
    }

    override suspend fun getTravelEstimates(
        originLat: Double,
        originLng: Double,
        eventIds: List<String>
    ): Result<Map<String, Int>> = runCatching {
        val response = eventsApi.getTravelEstimates(TravelEstimateRequest(originLat, originLng, eventIds))
        response.estimates.associate { it.eventId to it.durationMinutes }
    }
}
