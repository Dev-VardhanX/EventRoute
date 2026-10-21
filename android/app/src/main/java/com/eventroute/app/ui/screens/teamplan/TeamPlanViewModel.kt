package com.eventroute.app.ui.screens.teamplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.TeamPlan
import com.eventroute.app.domain.repository.TeamsRepository
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TeamPlanViewModel(
    private val teamsRepository: TeamsRepository = AppModule.teamsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<TeamPlan>>(UiState.Loading)
    val uiState: StateFlow<UiState<TeamPlan>> = _uiState.asStateFlow()

    fun loadPlan(teamId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            teamsRepository.getTeamPlan(teamId).fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Failed to load team plan") }
            )
        }
    }

    fun respondSuggestion(teamId: String, suggestionId: String, accept: Boolean) {
        viewModelScope.launch {
            teamsRepository.respondSuggestion(teamId, suggestionId, accept)
            loadPlan(teamId)
        }
    }
}
