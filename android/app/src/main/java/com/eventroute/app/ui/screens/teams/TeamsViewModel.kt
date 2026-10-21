package com.eventroute.app.ui.screens.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.Team
import com.eventroute.app.domain.repository.TeamsRepository
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TeamsViewModel(
    private val teamsRepository: TeamsRepository = AppModule.teamsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Team>>(UiState.Loading)
    val uiState: StateFlow<UiState<Team>> = _uiState.asStateFlow()

    fun loadTeam(teamId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            teamsRepository.getTeam(teamId).fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Failed to load team") }
            )
        }
    }

    fun joinTeam(inviteCode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            teamsRepository.joinTeam(inviteCode).fold(
                onSuccess = {
                    _uiState.value = UiState.Success(it)
                    onSuccess()
                },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Failed to join team") }
            )
        }
    }

    fun confirmTeam(teamId: String) {
        viewModelScope.launch {
            teamsRepository.confirmTeam(teamId).fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Failed to confirm team") }
            )
        }
    }
}
