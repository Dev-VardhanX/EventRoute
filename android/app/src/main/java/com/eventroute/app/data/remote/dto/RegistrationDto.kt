package com.eventroute.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegistrationDto(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("team_id") val teamId: String? = null,
    val status: String = "registered",
    @SerialName("ticket_token") val ticketToken: String? = null,
    val event: EventDto? = null
)

@Serializable
data class RegistrationListResponse(
    val items: List<RegistrationDto>,
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20
)
