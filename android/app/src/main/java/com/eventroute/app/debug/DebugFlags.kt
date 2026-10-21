package com.eventroute.app.debug

import com.eventroute.app.BuildConfig
import kotlinx.coroutines.delay

enum class SimulatedError(val code: String, val description: String) {
    EVENT_FULL("EVENT_FULL", "This event has reached full capacity."),
    EVENT_CLOSED("EVENT_CLOSED", "Registration for this event has closed."),
    ALREADY_REGISTERED("ALREADY_REGISTERED", "You are already registered for this event."),
    TEAM_FULL("TEAM_FULL", "This team already has the maximum allowed members."),
    NOT_ELIGIBLE("NOT_ELIGIBLE", "You are not eligible for this event."),
    TIME_CLASH("TIME_CLASH", "You have another event scheduled at this time."),
    INVITE_INVALID("INVITE_INVALID", "This invite code is invalid or has expired."),
    ROUTE_UNAVAILABLE("ROUTE_UNAVAILABLE", "No transit route could be calculated for this destination."),
    RATE_LIMITED("RATE_LIMITED", "Too many requests. Please slow down and try again."),
    UNAUTHENTICATED("UNAUTHENTICATED", "Session expired. Please sign in again."),
    NETWORK("NETWORK", "Network error. Please check your connection.");

    fun toException(): Exception = when (this) {
        NETWORK -> java.io.IOException(description)
        UNAUTHENTICATED -> java.lang.SecurityException(description)
        else -> IllegalStateException("[$code] $description")
    }
}

object DebugFlags {
    var simulatedError: SimulatedError? = null
    var latencyMs: Long = 0L
    var isOffline: Boolean = false

    suspend fun checkAndSimulate(tag: String? = null) {
        if (!BuildConfig.DEBUG) return

        if (isOffline) {
            throw java.io.IOException("Network unavailable (Offline mode simulated)")
        }

        if (latencyMs > 0) {
            delay(latencyMs)
        }

        simulatedError?.let { err ->
            simulatedError = null // consume once for the next call
            throw err.toException()
        }
    }
}
