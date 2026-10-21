package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.AppNotification

interface NotificationsRepository {
    suspend fun getNotifications(): Result<List<AppNotification>>
    suspend fun markAsRead(id: String): Result<Unit>
}
