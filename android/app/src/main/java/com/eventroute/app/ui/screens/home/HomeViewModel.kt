package com.eventroute.app.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.*
import com.eventroute.app.domain.repository.EventsRepository
import com.eventroute.app.domain.repository.ProfileRepository
import com.eventroute.app.ui.components.UiState
import com.eventroute.app.util.LocationProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeState(
    val eventsState: UiState<List<Event>> = UiState.Loading,
    val userProfile: User? = null,
    val userLocation: GeoPoint? = null,
    val currentQuery: EventQuery = EventQuery()
) {
    val searchQuery: String get() = currentQuery.text
    val selectedCategory: String get() = currentQuery.category
}

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val eventsRepository: EventsRepository = AppModule.eventsRepository,
    private val profileRepository: ProfileRepository = AppModule.profileRepository,
    private val locationProvider: LocationProvider = AppModule.locationProvider
) : ViewModel() {

    val isMapView: StateFlow<Boolean> = savedStateHandle.getStateFlow("isMapView", false)

    var cameraPosition: com.google.android.gms.maps.model.CameraPosition =
        com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            com.google.android.gms.maps.model.LatLng(28.6139, 77.2090), 10.5f
        )

    fun setMapView(isMap: Boolean) {
        savedStateHandle["isMapView"] = isMap
    }

    private val _queryFlow = MutableStateFlow(
        EventQuery(
            text = savedStateHandle.get<String>("searchQuery") ?: "",
            category = savedStateHandle.get<String>("category") ?: "All Events"
        )
    )

    private val _uiState = MutableStateFlow(HomeState(currentQuery = _queryFlow.value))
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    private var cachedUserLocation: GeoPoint? = null
    private var cachedProfile: User? = null

    init {
        initUserData()
        setupQuerySubscription()
    }

    private fun initUserData() {
        viewModelScope.launch {
            cachedProfile = profileRepository.getProfile().getOrNull()
            cachedUserLocation = locationProvider.getCurrentLocation()
            _uiState.update {
                it.copy(
                    userProfile = cachedProfile,
                    userLocation = cachedUserLocation
                )
            }
            // re-trigger query with location coordinates
            _queryFlow.update {
                it.copy(
                    userLat = cachedUserLocation?.lat,
                    userLng = cachedUserLocation?.lng
                )
            }
        }
    }

    private fun setupQuerySubscription() {
        viewModelScope.launch {
            _queryFlow
                .debounce { query ->
                    // 300ms debounce on search text
                    300L
                }
                .distinctUntilChanged()
                .collectLatest { query ->
                    executeQuery(query)
                }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            executeQuery(_queryFlow.value)
        }
    }

    fun onSearchQueryChange(queryText: String) {
        savedStateHandle["searchQuery"] = queryText
        _queryFlow.update { it.copy(text = queryText) }
        _uiState.update { it.copy(currentQuery = it.currentQuery.copy(text = queryText)) }
    }

    fun onCategorySelect(category: String) {
        savedStateHandle["category"] = category
        _queryFlow.update { it.copy(category = category) }
        _uiState.update { it.copy(currentQuery = it.currentQuery.copy(category = category)) }
    }

    fun applyFilterQuery(updatedQuery: EventQuery) {
        val queryWithLoc = updatedQuery.copy(
            userLat = cachedUserLocation?.lat,
            userLng = cachedUserLocation?.lng
        )
        savedStateHandle["category"] = queryWithLoc.category
        savedStateHandle["searchQuery"] = queryWithLoc.text
        _queryFlow.value = queryWithLoc
        _uiState.update { it.copy(currentQuery = queryWithLoc) }
    }

    fun resetFilters() {
        val defaultQuery = EventQuery(
            userLat = cachedUserLocation?.lat,
            userLng = cachedUserLocation?.lng
        )
        savedStateHandle.remove<String>("searchQuery")
        savedStateHandle.remove<String>("category")
        _queryFlow.value = defaultQuery
        _uiState.update { it.copy(currentQuery = defaultQuery) }
    }

    private suspend fun executeQuery(query: EventQuery) {
        _uiState.update { it.copy(eventsState = UiState.Loading) }
        val result = eventsRepository.queryEvents(query)
        result.fold(
            onSuccess = { events ->
                _uiState.update {
                    it.copy(
                        eventsState = if (events.isEmpty()) UiState.Empty else UiState.Success(events),
                        currentQuery = query
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        eventsState = UiState.Error(error.message ?: "Failed to load events"),
                        currentQuery = query
                    )
                }
            }
        )
    }
}
