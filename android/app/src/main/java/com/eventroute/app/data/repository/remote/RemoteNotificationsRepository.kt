package com.eventroute.app.data.repository.remote

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.api.NotificationsApi
import com.eventroute.app.domain.model.AppNotification
import com.eventroute.app.domain.repository.NotificationsRepository

class RemoteNotificationsRepository(
    private val notificationsApi: NotificationsApi
) : NotificationsRepository {

    override suspend fun getNotifications(): Result<List<AppNotification>> = runCatching {
        val response = notificationsApi.getNotifications()
        response.items.map { it.toDomain() }
    }

    override suspend fun markAsRead(id: String): Result<Unit> = runCatching {
        notificationsApi.markAsRead(id)
        Unit
    }
}
