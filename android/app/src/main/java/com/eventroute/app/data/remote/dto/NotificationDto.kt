package com.eventroute.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_read") val isRead: Boolean = false,
    val data: Map<String, String>? = null
)

@Serializable
data class NotificationListResponse(
    val items: List<NotificationDto>,
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20
)
