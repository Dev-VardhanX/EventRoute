package com.eventroute.app.data.mapper

import com.eventroute.app.data.remote.dto.NotificationDto
import com.eventroute.app.domain.model.AppNotification

fun NotificationDto.toDomain(): AppNotification = AppNotification(
    id = id,
    type = type,
    title = title,
    body = body,
    timeAgo = "Recently",
    isRead = isRead,
    rawTimestamp = createdAt
)
