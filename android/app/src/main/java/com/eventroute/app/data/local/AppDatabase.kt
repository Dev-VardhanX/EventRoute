package com.eventroute.app.data.local

import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.model.Journey
import com.eventroute.app.domain.model.User

/**
 * Local storage abstraction for offline-first EventRoute caching.
 * Supports caching "My Events", active journey plans, and user preferences.
 */
interface AppDatabase {
    suspend fun getCachedEvents(): List<Event>
    suspend fun saveEvents(events: List<Event>)
    suspend fun getRegisteredEvents(): List<Event>
    suspend fun saveRegistration(event: Event)
    suspend fun removeRegistration(eventId: String)
    suspend fun getSavedJourney(eventId: String): Journey?
    suspend fun saveJourney(journey: Journey)
    suspend fun getCachedUser(): User?
    suspend fun saveUser(user: User)
    suspend fun getSavedEventIds(): Set<String>
    suspend fun toggleSavedEvent(eventId: String): Boolean
    suspend fun clear()
}

class InMemoryAppDatabase : AppDatabase {
    private val cachedEvents = mutableListOf<Event>()
    private val registeredEvents = mutableListOf<Event>()
    private val cachedJourneys = mutableMapOf<String, Journey>()
    private val savedEventIds = mutableSetOf<String>()
    private var cachedUser: User? = null

    override suspend fun getCachedEvents(): List<Event> = cachedEvents.toList()

    override suspend fun saveEvents(events: List<Event>) {
        cachedEvents.clear()
        cachedEvents.addAll(events)
    }

    override suspend fun getRegisteredEvents(): List<Event> = registeredEvents.toList()

    override suspend fun saveRegistration(event: Event) {
        if (registeredEvents.none { it.id == event.id }) {
            registeredEvents.add(event)
        }
    }

    override suspend fun removeRegistration(eventId: String) {
        registeredEvents.removeAll { it.id == eventId }
    }

    override suspend fun getSavedJourney(eventId: String): Journey? = cachedJourneys[eventId]

    override suspend fun saveJourney(journey: Journey) {
        cachedJourneys[journey.eventId] = journey
    }

    override suspend fun getCachedUser(): User? = cachedUser

    override suspend fun saveUser(user: User) {
        cachedUser = user
    }

    override suspend fun getSavedEventIds(): Set<String> = savedEventIds.toSet()

    override suspend fun toggleSavedEvent(eventId: String): Boolean {
        return if (savedEventIds.contains(eventId)) {
            savedEventIds.remove(eventId)
            false
        } else {
            savedEventIds.add(eventId)
            true
        }
    }

    override suspend fun clear() {
        cachedEvents.clear()
        registeredEvents.clear()
        cachedJourneys.clear()
        savedEventIds.clear()
        cachedUser = null
    }
}
