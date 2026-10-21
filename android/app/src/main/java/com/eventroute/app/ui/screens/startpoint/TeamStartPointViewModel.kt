package com.eventroute.app.ui.screens.startpoint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.domain.model.TeamPlan
import com.eventroute.app.domain.repository.TeamsRepository
import com.eventroute.app.util.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StartPointOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val point: GeoPoint
)

class TeamStartPointViewModel(
    private val teamsRepository: TeamsRepository = AppModule.teamsRepository,
    private val locationProvider: LocationProvider = AppModule.locationProvider
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _updatedPlan = MutableStateFlow<TeamPlan?>(null)
    val updatedPlan: StateFlow<TeamPlan?> = _updatedPlan.asStateFlow()

    fun updateStartPoint(
        teamId: String,
        lat: Double,
        lng: Double,
        label: String,
        pref: String,
        onComplete: (Int) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            teamsRepository.setMyStartPoint(teamId, lat, lng, label, pref).fold(
                onSuccess = { plan ->
                    _updatedPlan.value = plan
                    _isSaving.value = false
                    onComplete(plan.planVersion)
                },
                onFailure = {
                    _isSaving.value = false
                    onComplete(1)
                }
            )
        }
    }

    suspend fun getFusedLocation(): GeoPoint {
        return locationProvider.getCurrentLocation()
    }
}
