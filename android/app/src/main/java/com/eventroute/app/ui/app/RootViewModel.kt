package com.eventroute.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.BuildConfig
import com.eventroute.app.data.remote.SessionEvents
import com.eventroute.app.data.remote.TokenStore
import com.eventroute.app.di.AppModule
import com.eventroute.app.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Decides initial navigation graph at launch and exposes session logout stream.
 */
class RootViewModel(
    private val tokenStore: TokenStore = AppModule.tokenStore,
    private val sessionEvents: SessionEvents = AppModule.sessionEvents,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    val loggedOut: SharedFlow<Unit> = sessionEvents.loggedOut

    init {
        resolveStart()
    }

    private fun resolveStart() {
        viewModelScope.launch {
            if (BuildConfig.USE_MOCK && tokenStore.access == null && tokenStore.refresh == null) {
                tokenStore.save("mock_jwt_access_token", "mock_jwt_refresh_token")
            }
            val hasSession = !tokenStore.access.isNullOrBlank()
            _startDestination.value = if (hasSession) {
                Screen.MainGraph.route
            } else {
                Screen.AuthGraph.route
            }
            _isReady.value = true
        }
    }
}
