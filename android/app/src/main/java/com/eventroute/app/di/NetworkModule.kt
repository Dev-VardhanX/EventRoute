package com.eventroute.app.di

import com.eventroute.app.data.remote.NetworkModule as RemoteNetworkFactory
import com.eventroute.app.data.remote.SessionEvents
import com.eventroute.app.data.remote.TokenStore

/**
 * Dependency injection module providing networking stack singletons.
 */
object NetworkModule {
    private var instance: RemoteNetworkFactory? = null

    fun init(tokenStore: TokenStore, sessionEvents: SessionEvents) {
        if (instance == null) {
            instance = RemoteNetworkFactory(tokenStore, sessionEvents)
        }
    }

    val networkModule: RemoteNetworkFactory
        get() = instance ?: throw IllegalStateException("NetworkModule must be initialized before access")
}
