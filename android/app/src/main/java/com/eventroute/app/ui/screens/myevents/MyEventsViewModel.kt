package com.eventroute.app.ui.screens.myevents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.repository.RegistrationRepository
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyEventsViewModel(
    private val registrationRepository: RegistrationRepository = AppModule.registrationRepository,
    private val savedEventsRepository: com.eventroute.app.domain.repository.SavedEventsRepository = AppModule.savedEventsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Event>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Event>>> = _uiState.asStateFlow()

    private val _savedUiState = MutableStateFlow<UiState<List<Event>>>(UiState.Loading)
    val savedUiState: StateFlow<UiState<List<Event>>> = _savedUiState.asStateFlow()

    init {
        loadEvents()
        loadSavedEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            registrationRepository.getMyRegistrations().fold(
                onSuccess = { events ->
                    _uiState.value = if (events.isEmpty()) UiState.Empty else UiState.Success(events)
                },
                onFailure = { err ->
                    _uiState.value = UiState.Error(err.message ?: "Failed to load registered events")
                }
            )
        }
    }

    fun loadSavedEvents() {
        viewModelScope.launch {
            _savedUiState.value = UiState.Loading
            savedEventsRepository.getSavedEvents().fold(
                onSuccess = { events ->
                    _savedUiState.value = if (events.isEmpty()) UiState.Empty else UiState.Success(events)
                },
                onFailure = { err ->
                    _savedUiState.value = UiState.Error(err.message ?: "Failed to load saved events")
                }
            )
        }
    }
}
