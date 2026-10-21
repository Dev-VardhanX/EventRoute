package com.eventroute.app.service

import android.util.Log
import com.eventroute.app.BuildConfig
import com.eventroute.app.di.AppModule
import com.eventroute.app.notifications.NotificationRouter
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FcmTokenHolder {
    var latestToken: String? = null
}

class AppMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data["type"] ?: return
        val eventId = data["event_id"]
        val teamId = data["team_id"]
        val title = data["title"] ?: remoteMessage.notification?.title
        val body = data["body"] ?: remoteMessage.notification?.body

        serviceScope.launch {
            val settings = AppModule.notificationSettingsRepository.getSettings()
            NotificationRouter.showNotification(
                context = applicationContext,
                type = type,
                eventId = eventId,
                teamId = teamId,
                title = title,
                body = body,
                settings = settings
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenHolder.latestToken = token
        if (BuildConfig.DEBUG) {
            Log.d("FCM", "New device token: $token")
        }

        serviceScope.launch {
            runCatching {
                AppModule.profileRepository.registerDeviceToken(token)
            }
        }
    }
}
