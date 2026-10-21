package com.eventroute.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.AppNotification
import com.eventroute.app.domain.repository.NotificationsRepository
import com.eventroute.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository = AppModule.notificationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<AppNotification>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<AppNotification>>> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            notificationsRepository.getNotifications().fold(
                onSuccess = { list ->
                    _uiState.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
                },
                onFailure = { err ->
                    _uiState.value = UiState.Error(err.message ?: "Failed to load notifications")
                }
            )
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            notificationsRepository.markAsRead(id)
            val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
            val updated = current.map { if (it.id == id) it.copy(isRead = true) else it }
            _uiState.value = UiState.Success(updated)
        }
    }
}
