package com.eventroute.app.di

import android.content.Context
import com.eventroute.app.data.local.AppDatabase
import com.eventroute.app.data.remote.EncryptedTokenStore
import com.eventroute.app.data.remote.SessionEvents
import com.eventroute.app.data.remote.TokenStore
import com.eventroute.app.domain.repository.*
import com.eventroute.app.util.LocationProvider

/**
 * EventRoute Core DI Provider.
 * Coordinates [DatabaseModule], [NetworkModule], and [RepositoryModule] singletons.
 */
object AppModule {
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            val app = context.applicationContext
            appContext = app
            DatabaseModule.init(app)
            NetworkModule.init(tokenStore, sessionEvents)
            RepositoryModule.init(app, tokenStore, database, networkModule)
        }
    }

    private fun getContext(): Context {
        return appContext ?: throw IllegalStateException("AppModule must be initialized with Context")
    }

    val tokenStore: TokenStore by lazy {
        EncryptedTokenStore(getContext())
    }

    val sessionEvents: SessionEvents by lazy {
        SessionEvents()
    }

    val database: AppDatabase
        get() = DatabaseModule.database

    val networkModule: com.eventroute.app.data.remote.NetworkModule
        get() = NetworkModule.networkModule

    val locationProvider: LocationProvider by lazy {
        LocationProvider(getContext(), profileRepository)
    }

    val notificationSettingsRepository: com.eventroute.app.notifications.NotificationSettingsRepository by lazy {
        com.eventroute.app.notifications.NotificationSettingsRepositoryImpl(getContext())
    }

    val reminderScheduler: com.eventroute.app.notifications.ReminderScheduler by lazy {
        com.eventroute.app.notifications.ReminderScheduler(getContext())
    }

    val savedEventsRepository: com.eventroute.app.domain.repository.SavedEventsRepository by lazy {
        com.eventroute.app.domain.repository.SavedEventsRepositoryImpl(database, eventsRepository)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delegated repository accessors preserving exact backward compatibility
    // ─────────────────────────────────────────────────────────────────────────

    val authRepository: AuthRepository
        get() = RepositoryModule.authRepository

    val profileRepository: ProfileRepository
        get() = RepositoryModule.profileRepository

    val eventsRepository: EventsRepository
        get() = RepositoryModule.eventsRepository

    val eventRepository: EventsRepository
        get() = eventsRepository

    val registrationRepository: RegistrationRepository
        get() = RepositoryModule.registrationRepository

    val teamsRepository: TeamsRepository
        get() = RepositoryModule.teamsRepository

    val teamRepository: TeamsRepository
        get() = teamsRepository

    val journeyRepository: JourneyRepository
        get() = RepositoryModule.journeyRepository

    val notificationsRepository: NotificationsRepository
        get() = RepositoryModule.notificationsRepository

    val liveTeamRepository: LiveTeamRepository
        get() = RepositoryModule.liveTeamRepository

    val liveLocationRepository: LiveLocationRepository
        get() = RepositoryModule.liveLocationRepository
}
