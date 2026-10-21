package com.eventroute.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

enum class NotifChannel(val id: String, val title: String, val importance: Int) {
    REMINDERS("reminders", "Leave & event reminders", NotificationManager.IMPORTANCE_HIGH),
    TEAM("team", "Team updates", NotificationManager.IMPORTANCE_DEFAULT),
    ALERTS("alerts", "Delays & event changes", NotificationManager.IMPORTANCE_HIGH),
    ANNOUNCEMENTS("announcements", "Announcements", NotificationManager.IMPORTANCE_LOW),
    JOURNEY("journey", "Live journey", NotificationManager.IMPORTANCE_LOW),   // foreground service
}

object NotificationChannels {
    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            NotifChannel.entries.forEach { channel ->
                val notifChannel = NotificationChannel(channel.id, channel.title, channel.importance).apply {
                    description = channel.title
                }
                manager.createNotificationChannel(notifChannel)
            }
        }
    }
}
