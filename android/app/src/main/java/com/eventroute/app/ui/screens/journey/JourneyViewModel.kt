package com.eventroute.app.ui.screens.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.Journey
import com.eventroute.app.domain.model.Route
import com.eventroute.app.domain.repository.JourneyRepository
import com.eventroute.app.ui.components.UiState
import com.eventroute.app.util.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JourneyUiData(
    val journey: Journey,
    val selectedRouteIndex: Int = 0,
    val selectedRoute: Route? = null
)

class JourneyViewModel(
    private val journeyRepository: JourneyRepository = AppModule.journeyRepository,
    private val locationProvider: LocationProvider = AppModule.locationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<JourneyUiData>>(UiState.Loading)
    val uiState: StateFlow<UiState<JourneyUiData>> = _uiState.asStateFlow()

    fun loadJourney(eventId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val loc = locationProvider.getCurrentLocation()
            journeyRepository.getJourneyPlan(eventId, loc.lat, loc.lng).fold(
                onSuccess = { journey ->
                    val defaultRoute = journey.routes.firstOrNull()
                    _uiState.value = UiState.Success(
                        JourneyUiData(
                            journey = journey,
                            selectedRouteIndex = 0,
                            selectedRoute = defaultRoute
                        )
                    )
                },
                onFailure = { err ->
                    _uiState.value = UiState.Error(err.message ?: "Failed to plan journey")
                }
            )
        }
    }

    fun selectRoute(index: Int) {
        val current = (_uiState.value as? UiState.Success)?.data ?: return
        if (index in current.journey.routes.indices) {
            val route = current.journey.routes[index]
            _uiState.value = UiState.Success(
                current.copy(
                    selectedRouteIndex = index,
                    selectedRoute = route
                )
            )
        }
    }
}
