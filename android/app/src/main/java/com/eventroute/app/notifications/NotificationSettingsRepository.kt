package com.eventroute.app.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_settings")

interface NotificationSettingsRepository {
    val settingsFlow: Flow<NotificationSettings>
    suspend fun getSettings(): NotificationSettings
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setTeamEnabled(enabled: Boolean)
    suspend fun setAlertsEnabled(enabled: Boolean)
    suspend fun setAnnouncementsEnabled(enabled: Boolean)
    suspend fun setQuietHoursEnabled(enabled: Boolean)
    suspend fun setQuietHoursStart(time: LocalTime)
    suspend fun setQuietHoursEnd(time: LocalTime)
    suspend fun resetToDefaults()
}

class NotificationSettingsRepositoryImpl(
    private val context: Context
) : NotificationSettingsRepository {

    private object PreferencesKeys {
        val REMINDERS = booleanPreferencesKey("notif_reminders")
        val TEAM = booleanPreferencesKey("notif_team")
        val ALERTS = booleanPreferencesKey("notif_alerts")
        val ANNOUNCEMENTS = booleanPreferencesKey("notif_announcements")
        val QUIET_HOURS = booleanPreferencesKey("notif_quiet_hours")
        val QUIET_START_H = intPreferencesKey("notif_quiet_start_h")
        val QUIET_START_M = intPreferencesKey("notif_quiet_start_m")
        val QUIET_END_H = intPreferencesKey("notif_quiet_end_h")
        val QUIET_END_M = intPreferencesKey("notif_quiet_end_m")
    }

    override val settingsFlow: Flow<NotificationSettings> = context.notificationDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            NotificationSettings(
                remindersEnabled = prefs[PreferencesKeys.REMINDERS] ?: true,
                teamEnabled = prefs[PreferencesKeys.TEAM] ?: true,
                alertsEnabled = prefs[PreferencesKeys.ALERTS] ?: true,
                announcementsEnabled = prefs[PreferencesKeys.ANNOUNCEMENTS] ?: true,
                quietHoursEnabled = prefs[PreferencesKeys.QUIET_HOURS] ?: false,
                quietHoursStart = LocalTime.of(
                    prefs[PreferencesKeys.QUIET_START_H] ?: 22,
                    prefs[PreferencesKeys.QUIET_START_M] ?: 0
                ),
                quietHoursEnd = LocalTime.of(
                    prefs[PreferencesKeys.QUIET_END_H] ?: 7,
                    prefs[PreferencesKeys.QUIET_END_M] ?: 0
                )
            )
        }

    override suspend fun getSettings(): NotificationSettings {
        return settingsFlow.first()
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[PreferencesKeys.REMINDERS] = enabled }
    }

    override suspend fun setTeamEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[PreferencesKeys.TEAM] = enabled }
    }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[PreferencesKeys.ALERTS] = enabled }
    }

    override suspend fun setAnnouncementsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[PreferencesKeys.ANNOUNCEMENTS] = enabled }
    }

    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[PreferencesKeys.QUIET_HOURS] = enabled }
    }

    override suspend fun setQuietHoursStart(time: LocalTime) {
        context.notificationDataStore.edit {
            it[PreferencesKeys.QUIET_START_H] = time.hour
            it[PreferencesKeys.QUIET_START_M] = time.minute
        }
    }

    override suspend fun setQuietHoursEnd(time: LocalTime) {
        context.notificationDataStore.edit {
            it[PreferencesKeys.QUIET_END_H] = time.hour
            it[PreferencesKeys.QUIET_END_M] = time.minute
        }
    }

    override suspend fun resetToDefaults() {
        setRemindersEnabled(true)
        setTeamEnabled(true)
        setAlertsEnabled(true)
        setAnnouncementsEnabled(true)
        setQuietHoursEnabled(false)
    }
}
