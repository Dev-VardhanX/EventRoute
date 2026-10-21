package com.eventroute.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventroute.app.di.AppModule
import com.eventroute.app.notifications.NotificationSettings
import com.eventroute.app.notifications.NotificationSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class NotificationSettingsViewModel(
    private val repo: NotificationSettingsRepository = AppModule.notificationSettingsRepository
) : ViewModel() {

    val settings: StateFlow<NotificationSettings> = repo.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NotificationSettings()
        )

    fun toggleReminders(enabled: Boolean) = viewModelScope.launch { repo.setRemindersEnabled(enabled) }
    fun toggleTeam(enabled: Boolean) = viewModelScope.launch { repo.setTeamEnabled(enabled) }
    fun toggleAlerts(enabled: Boolean) = viewModelScope.launch { repo.setAlertsEnabled(enabled) }
    fun toggleAnnouncements(enabled: Boolean) = viewModelScope.launch { repo.setAnnouncementsEnabled(enabled) }
    fun toggleQuietHours(enabled: Boolean) = viewModelScope.launch { repo.setQuietHoursEnabled(enabled) }
    fun setQuietStart(time: LocalTime) = viewModelScope.launch { repo.setQuietHoursStart(time) }
    fun setQuietEnd(time: LocalTime) = viewModelScope.launch { repo.setQuietHoursEnd(time) }
}
