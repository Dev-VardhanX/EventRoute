package com.eventroute.app.data.repository.mock

import android.content.Context
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.mock.AssetReader
import com.eventroute.app.data.remote.dto.NotificationListResponse
import com.eventroute.app.domain.model.AppNotification
import com.eventroute.app.domain.repository.NotificationsRepository
import kotlinx.coroutines.delay

class MockNotificationsRepository(
    private val context: Context
) : NotificationsRepository {

    private var notifications: MutableList<AppNotification>? = null

    private fun load(): List<AppNotification> {
        if (notifications == null) {
            val response: NotificationListResponse = AssetReader.readJsonAsset(context, "mock/notifications.json")
            notifications = response.items.map { it.toDomain() }.toMutableList()
        }
        return notifications!!
    }

    override suspend fun getNotifications(): Result<List<AppNotification>> {
        delay(250)
        return Result.success(load())
    }

    override suspend fun markAsRead(id: String): Result<Unit> {
        delay(150)
        notifications = notifications?.map {
            if (it.id == id) it.copy(isRead = true) else it
        }?.toMutableList()
        return Result.success(Unit)
    }
}
