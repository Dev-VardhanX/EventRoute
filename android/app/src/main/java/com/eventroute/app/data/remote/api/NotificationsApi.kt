package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.NotificationListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

interface NotificationsApi {
    @GET("me/notifications")
    suspend fun getNotifications(): NotificationListResponse

    @POST("me/notifications/{id}/read")
    suspend fun markAsRead(@Path("id") id: String): Map<String, Boolean>
}
