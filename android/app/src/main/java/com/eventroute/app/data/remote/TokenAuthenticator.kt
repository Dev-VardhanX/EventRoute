package com.eventroute.app.data.remote

import com.eventroute.app.data.remote.api.AuthRefreshApi
import com.eventroute.app.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokens: TokenStore,
    private val refreshApi: () -> AuthRefreshApi,
    private val session: SessionEvents,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // retry at most once
        synchronized(this) {
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = tokens.access
            if (current != null && current != sent) {
                // Another thread already refreshed the token
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val refresh = tokens.refresh ?: run {
                tokens.clear()
                session.loggedOut()
                return null
            }
            val result = runBlocking {
                runCatching { refreshApi().refresh(RefreshRequest(refresh)) }.getOrNull()
            } ?: run {
                tokens.clear()
                session.loggedOut()
                return null
            }
            tokens.save(result.accessToken, result.refreshToken)
            return response.request.newBuilder().header("Authorization", "Bearer ${result.accessToken}").build()
        }
    }
}
