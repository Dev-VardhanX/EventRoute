package com.eventroute.app.ui.screens.venuemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.repository.EventsRepository
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VenueMapViewModel(
    private val eventsRepository: EventsRepository = AppModule.eventsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Event>>(UiState.Loading)
    val uiState: StateFlow<UiState<Event>> = _uiState.asStateFlow()

    fun loadVenue(eventId: String) {
        if (eventId.isBlank()) {
            _uiState.value = UiState.Empty
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            eventsRepository.getEvent(eventId).fold(
                onSuccess = { event ->
                    if (event.venueLat == 0.0 && event.venueLng == 0.0) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Success(event)
                    }
                },
                onFailure = { error ->
                    if (error is NoSuchElementException || error.message?.contains("not found", ignoreCase = true) == true) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Error(error.message ?: "Failed to load venue map")
                    }
                }
            )
        }
    }
}
