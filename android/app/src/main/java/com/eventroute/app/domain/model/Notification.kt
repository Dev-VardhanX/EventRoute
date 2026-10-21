package com.eventroute.app.domain.model

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val timeAgo: String,
    val isRead: Boolean = false,
    val rawTimestamp: String = ""
)
