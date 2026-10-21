package com.eventroute.app.domain.usecase

import com.eventroute.app.data.remote.ApiException

fun ApiException.friendly(): String = when (code) {
    "EVENT_FULL" -> "This event is full."
    "EVENT_CLOSED" -> "Registration for this event is closed."
    "ALREADY_REGISTERED" -> "You are already registered for this event."
    "TEAM_FULL" -> "This team is already full."
    "TEAM_TOO_SMALL" -> "Your team needs more members before it can be confirmed."
    "NOT_ELIGIBLE" -> "You are not eligible for this event."
    "TIME_CLASH" -> "This overlaps with another event you registered for."
    "INVITE_INVALID" -> "That invite code is not valid."
    "ROUTE_UNAVAILABLE" -> "We couldn't find a route right now. Try again."
    "RATE_LIMITED" -> "Too many requests. Please wait a moment."
    "UNAUTHENTICATED" -> "Please log in again."
    "NETWORK" -> "No connection. Check your internet and retry."
    else -> message
}
