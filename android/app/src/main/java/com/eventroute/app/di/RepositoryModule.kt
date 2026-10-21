package com.eventroute.app.di

import android.content.Context
import com.eventroute.app.data.local.AppDatabase
import com.eventroute.app.data.remote.ApiMode
import com.eventroute.app.data.remote.TokenStore
import com.eventroute.app.data.repository.mock.*
import com.eventroute.app.data.repository.remote.*
import com.eventroute.app.domain.repository.*

/**
 * Dependency injection module providing repositories with per-endpoint mock/remote switching via [ApiMode].
 */
object RepositoryModule {
    private var appContext: Context? = null
    private var tokenStore: TokenStore? = null
    private var database: AppDatabase? = null
    private var network: com.eventroute.app.data.remote.NetworkModule? = null

    fun init(
        context: Context,
        tokenStore: TokenStore,
        database: AppDatabase,
        network: com.eventroute.app.data.remote.NetworkModule,
    ) {
        if (appContext == null) {
            appContext = context.applicationContext
            this.tokenStore = tokenStore
            this.database = database
            this.network = network
            com.eventroute.app.domain.live.LiveFeedSimulator.init(context)
        }
    }

    private fun getContext(): Context =
        appContext ?: throw IllegalStateException("RepositoryModule not initialized")

    private fun getTokens(): TokenStore =
        tokenStore ?: throw IllegalStateException("RepositoryModule not initialized")

    private fun getDb(): AppDatabase =
        database ?: throw IllegalStateException("RepositoryModule not initialized")

    private fun getNet(): com.eventroute.app.data.remote.NetworkModule =
        network ?: throw IllegalStateException("RepositoryModule not initialized")

    val mockAuthRepository: MockAuthRepository by lazy {
        MockAuthRepository(getContext(), getTokens())
    }
    val remoteAuthRepository: RemoteAuthRepository by lazy {
        RemoteAuthRepository(getNet().authApi, getNet().meApi, getTokens())
    }
    val authRepository: AuthRepository
        get() = if (ApiMode.isMock("auth")) mockAuthRepository else remoteAuthRepository

    val mockProfileRepository: MockProfileRepository by lazy {
        MockProfileRepository(getContext())
    }
    val remoteProfileRepository: RemoteProfileRepository by lazy {
        RemoteProfileRepository(getNet().meApi, getNet().collegesApi)
    }
    val profileRepository: ProfileRepository
        get() = if (ApiMode.isMock("me")) mockProfileRepository else remoteProfileRepository

    val mockEventsRepository: MockEventsRepository by lazy {
        MockEventsRepository(getContext())
    }
    val remoteEventsRepository: RemoteEventsRepository by lazy {
        RemoteEventsRepository(getNet().eventsApi)
    }
    val eventsRepository: EventsRepository
        get() = if (ApiMode.isMock("events")) mockEventsRepository else remoteEventsRepository
    val eventRepository: EventsRepository
        get() = eventsRepository

    val mockRegistrationRepository: MockRegistrationRepository by lazy {
        MockRegistrationRepository(getContext(), getDb())
    }
    val remoteRegistrationRepository: RemoteRegistrationRepository by lazy {
        RemoteRegistrationRepository(getNet().registrationApi, getDb())
    }
    val registrationRepository: RegistrationRepository
        get() = if (ApiMode.isMock("registration")) mockRegistrationRepository else remoteRegistrationRepository

    val mockTeamsRepository: MockTeamsRepository by lazy {
        MockTeamsRepository(getContext())
    }
    val remoteTeamsRepository: RemoteTeamsRepository by lazy {
        RemoteTeamsRepository(getNet().teamsApi)
    }
    val teamsRepository: TeamsRepository
        get() = if (ApiMode.isMock("teams")) mockTeamsRepository else remoteTeamsRepository
    val teamRepository: TeamsRepository
        get() = teamsRepository

    val mockJourneyRepository: MockJourneyRepository by lazy {
        MockJourneyRepository(getContext())
    }
    val remoteJourneyRepository: RemoteJourneyRepository by lazy {
        RemoteJourneyRepository(getNet().journeyApi)
    }
    val journeyRepository: JourneyRepository
        get() = if (ApiMode.isMock("journey")) mockJourneyRepository else remoteJourneyRepository

    val mockNotificationsRepository: MockNotificationsRepository by lazy {
        MockNotificationsRepository(getContext())
    }
    val remoteNotificationsRepository: RemoteNotificationsRepository by lazy {
        RemoteNotificationsRepository(getNet().notificationsApi)
    }
    val notificationsRepository: NotificationsRepository
        get() = if (ApiMode.isMock("notifications")) mockNotificationsRepository else remoteNotificationsRepository

    val mockLiveTeamRepository: MockLiveTeamRepository by lazy {
        MockLiveTeamRepository()
    }
    val remoteLiveTeamRepository: RemoteLiveTeamRepository by lazy {
        RemoteLiveTeamRepository()
    }
    val liveTeamRepository: LiveTeamRepository
        get() = if (ApiMode.isMock("live")) mockLiveTeamRepository else remoteLiveTeamRepository

    val mockLiveLocationRepository: MockLiveLocationRepository by lazy {
        MockLiveLocationRepository()
    }
    val remoteLiveLocationRepository: RemoteLiveLocationRepository by lazy {
        RemoteLiveLocationRepository()
    }
    val liveLocationRepository: LiveLocationRepository
        get() = if (ApiMode.isMock("live")) mockLiveLocationRepository else remoteLiveLocationRepository
}
