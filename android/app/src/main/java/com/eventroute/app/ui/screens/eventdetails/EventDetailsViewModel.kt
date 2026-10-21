package com.eventroute.app.ui.screens.eventdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.repository.EventsRepository
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventDetailsViewModel(
    private val eventsRepository: EventsRepository = AppModule.eventsRepository,
    private val savedEventsRepository: com.eventroute.app.domain.repository.SavedEventsRepository = AppModule.savedEventsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Event>>(UiState.Loading)
    val uiState: StateFlow<UiState<Event>> = _uiState.asStateFlow()

    private val _currentEventId = MutableStateFlow("")

    val isSaved: StateFlow<Boolean> = combine(
        _currentEventId,
        savedEventsRepository.savedEventIds
    ) { id, savedIds ->
        id.isNotBlank() && savedIds.contains(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun loadEvent(eventId: String) {
        _currentEventId.value = eventId
        if (eventId.isBlank()) {
            _uiState.value = UiState.Empty
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            eventsRepository.getEvent(eventId).fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = { error ->
                    if (error is NoSuchElementException || error.message?.contains("not found", ignoreCase = true) == true) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Error(error.message ?: "Failed to load event")
                    }
                }
            )
        }
    }

    fun toggleSave(eventId: String) {
        viewModelScope.launch {
            savedEventsRepository.toggle(eventId)
        }
    }
}
