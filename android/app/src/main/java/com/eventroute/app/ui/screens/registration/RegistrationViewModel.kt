package com.eventroute.app.ui.screens.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.model.Team
import com.eventroute.app.domain.repository.EventsRepository
import com.eventroute.app.domain.repository.RegistrationRepository
import com.eventroute.app.domain.repository.TeamsRepository
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegistrationState(
    val eventState: UiState<Event> = UiState.Loading,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val submissionError: String? = null
)

class RegistrationViewModel(
    private val eventsRepository: EventsRepository = AppModule.eventsRepository,
    private val registrationRepository: RegistrationRepository = AppModule.registrationRepository,
    private val teamsRepository: TeamsRepository = AppModule.teamsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationState())
    val uiState: StateFlow<RegistrationState> = _uiState.asStateFlow()

    fun loadEvent(eventId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(eventState = UiState.Loading)
            eventsRepository.getEvent(eventId).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(eventState = UiState.Success(it)) },
                onFailure = { _uiState.value = _uiState.value.copy(eventState = UiState.Error(it.message ?: "Failed to load event")) }
            )
        }
    }

    fun registerSolo(eventId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, submissionError = null)
            registrationRepository.registerSolo(eventId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, isSuccess = true)
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, submissionError = err.message)
                }
            )
        }
    }

    fun createTeam(eventId: String, teamName: String, onSuccess: (Team) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, submissionError = null)
            teamsRepository.createTeam(eventId, teamName).fold(
                onSuccess = { team ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, isSuccess = true)
                    onSuccess(team)
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, submissionError = err.message)
                }
            )
        }
    }
}
