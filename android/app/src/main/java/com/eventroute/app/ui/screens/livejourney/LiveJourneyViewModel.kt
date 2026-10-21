package com.eventroute.app.ui.screens.livejourney

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.domain.model.Team
import com.eventroute.app.domain.model.TeamMember
import com.eventroute.app.domain.repository.LiveTeamRepository
import com.eventroute.app.domain.repository.TeamsRepository
import com.eventroute.app.service.LocationForegroundService
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.eventroute.app.domain.repository.EventsRepository

data class LiveJourneyUiData(
    val teamId: String,
    val members: List<TeamMember>,
    val venueLocation: GeoPoint?,
    val venueName: String = "Venue",
    val alertMessage: String? = null,
    val targetArrivalAt: String = "09:30 AM"
)

class LiveJourneyViewModel(
    private val eventsRepository: EventsRepository = AppModule.eventsRepository,
    private val teamsRepository: TeamsRepository = AppModule.teamsRepository,
    private val liveTeamRepository: LiveTeamRepository = AppModule.liveTeamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<LiveJourneyUiData>>(UiState.Loading)
    val uiState: StateFlow<UiState<LiveJourneyUiData>> = _uiState.asStateFlow()

    val isServiceRunning: StateFlow<Boolean> = LocationForegroundService.isRunning

    private var currentTeamId: String = "team-bug-hunters"

    fun loadLiveJourney(eventId: String = "", teamId: String = "team-bug-hunters") {
        currentTeamId = teamId
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val event = if (eventId.isNotBlank()) eventsRepository.getEvent(eventId).getOrNull() else null
            val customVenueLoc = if (event != null && event.venueLat != 0.0) GeoPoint(event.venueLat, event.venueLng) else null
            val customVenueName = event?.venueName?.ifEmpty { event.title } ?: "IIT Delhi"

            liveTeamRepository.observeLiveState(teamId).collect { state ->
                _uiState.value = UiState.Success(
                    LiveJourneyUiData(
                        teamId = state.teamId,
                        members = state.members,
                        venueLocation = customVenueLoc ?: state.venueLocation,
                        venueName = customVenueName,
                        alertMessage = state.alertMessage,
                        targetArrivalAt = state.targetArrivalAt
                    )
                )
            }
        }
    }

    fun updateUserStatus(status: String) {
        liveTeamRepository.updateUserStatus(currentTeamId, status)
    }

    fun startLocationSharing(context: Context) {
        LocationForegroundService.start(context, teamId = currentTeamId)
    }

    fun stopLocationSharing(context: Context) {
        LocationForegroundService.stop(context)
    }
}
