package com.eventroute.app.domain.repository

import com.eventroute.app.data.local.AppDatabase
import com.eventroute.app.domain.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface SavedEventsRepository {
    val savedEventIds: StateFlow<Set<String>>
    suspend fun toggle(eventId: String): Boolean
    suspend fun isSaved(eventId: String): Boolean
    suspend fun getSavedEvents(): Result<List<Event>>
}

class SavedEventsRepositoryImpl(
    private val localDb: AppDatabase,
    private val eventsRepository: EventsRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SavedEventsRepository {

    private val _savedEventIds = MutableStateFlow<Set<String>>(emptySet())
    override val savedEventIds: StateFlow<Set<String>> = _savedEventIds.asStateFlow()

    init {
        scope.launch {
            _savedEventIds.value = localDb.getSavedEventIds()
        }
    }

    override suspend fun toggle(eventId: String): Boolean {
        val newState = localDb.toggleSavedEvent(eventId)
        _savedEventIds.value = localDb.getSavedEventIds()
        return newState
    }

    override suspend fun isSaved(eventId: String): Boolean {
        return _savedEventIds.value.contains(eventId)
    }

    override suspend fun getSavedEvents(): Result<List<Event>> {
        val ids = localDb.getSavedEventIds()
        if (ids.isEmpty()) return Result.success(emptyList())

        return eventsRepository.getEvents().map { allEvents ->
            allEvents.filter { ids.contains(it.id) }
        }
    }
}
