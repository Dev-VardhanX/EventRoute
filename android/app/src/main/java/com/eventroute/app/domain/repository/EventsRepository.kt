package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.model.EventQuery
import com.eventroute.app.domain.model.ParticipationFilter

interface EventsRepository {
    suspend fun getEvents(
        category: String? = null,
        searchQuery: String? = null,
        participation: String? = null
    ): Result<List<Event>>

    suspend fun queryEvents(query: EventQuery): Result<List<Event>> = getEvents(
        category = query.category,
        searchQuery = query.text,
        participation = when (query.participation) {
            ParticipationFilter.SOLO -> "solo"
            ParticipationFilter.TEAM -> "team"
            ParticipationFilter.EITHER -> "either"
            else -> null
        }
    )

    suspend fun getEventById(id: String): Result<Event>
    suspend fun getEvent(id: String): Result<Event> = getEventById(id)

    suspend fun getTravelEstimates(
        originLat: Double,
        originLng: Double,
        eventIds: List<String>
    ): Result<Map<String, Int>>
}
