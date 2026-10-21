package com.eventroute.app.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.eventroute.app.MainActivity
import com.eventroute.app.R
import java.time.LocalTime

object NotificationRouter {

    fun channelForType(type: String): NotifChannel? = when (type.lowercase()) {
        "registration_confirmed",
        "team_member_joined",
        "team_member_left",
        "team_confirmed",
        "team_incomplete",
        "join_suggestion" -> NotifChannel.TEAM

        "deadline_reminder",
        "event_reminder",
        "leave_soon",
        "leave_now" -> NotifChannel.REMINDERS

        "teammate_delayed",
        "team_late_risk",
        "event_updated",
        "event_cancelled" -> NotifChannel.ALERTS

        "announcement" -> NotifChannel.ANNOUNCEMENTS

        "journey" -> NotifChannel.JOURNEY

        else -> null
    }

    fun deepLinkFor(type: String, eventId: String? = null, teamId: String? = null): String? {
        val safeEventId = eventId?.ifBlank { "11111111-1111-1111-1111-111111111111" } ?: "11111111-1111-1111-1111-111111111111"
        val safeTeamId = teamId?.ifBlank { "team-bug-hunters" } ?: "team-bug-hunters"

        return when (type.lowercase()) {
            "registration_confirmed",
            "event_reminder",
            "deadline_reminder",
            "event_updated",
            "event_cancelled",
            "announcement" -> "eventroute://event/$safeEventId"

            "leave_soon",
            "leave_now" -> "eventroute://journey/$safeEventId"

            "team_member_joined",
            "team_member_left",
            "team_confirmed",
            "team_incomplete",
            "join_suggestion",
            "teammate_delayed",
            "team_late_risk" -> "eventroute://team/$safeTeamId/plan"

            else -> null
        }
    }

    fun defaultTitleAndBody(
        type: String,
        title: String? = null,
        body: String? = null
    ): Pair<String, String> {
        val defaultTitle = title ?: when (type.lowercase()) {
            "leave_soon" -> "Leave soon!"
            "leave_now" -> "Leave now!"
            "event_reminder" -> "Event Tomorrow"
            "deadline_reminder" -> "Registration Closing Soon"
            "registration_confirmed" -> "Registration Confirmed"
            "team_member_joined" -> "Teammate Joined"
            "team_member_left" -> "Teammate Left"
            "team_confirmed" -> "Team Confirmed"
            "team_incomplete" -> "Team Incomplete"
            "join_suggestion" -> "Join on the Way"
            "teammate_delayed" -> "Teammate Delayed"
            "team_late_risk" -> "Team Late Risk"
            "event_updated" -> "Event Updated"
            "event_cancelled" -> "Event Cancelled"
            "announcement" -> "Announcement"
            else -> "EventRoute Update"
        }

        val defaultBody = body ?: when (type.lowercase()) {
            "leave_soon" -> "Time to start heading out in 15 minutes."
            "leave_now" -> "Depart now to arrive at your venue on time."
            "event_reminder" -> "Your registered event begins in 24 hours."
            "deadline_reminder" -> "Seats are filling up quickly. Register now."
            "registration_confirmed" -> "Your pass is ready. View event details."
            "team_member_joined" -> "A new teammate has joined your squad."
            "team_member_left" -> "A teammate left your group."
            "team_confirmed" -> "Your team plan is locked in. View arrival times."
            "team_incomplete" -> "Your team still needs members."
            "join_suggestion" -> "A teammate has a shared transit suggestion."
            "teammate_delayed" -> "A teammate is running behind schedule."
            "team_late_risk" -> "Traffic delay detected. Review updated plan."
            "event_updated" -> "Details or venue schedule have changed."
            "event_cancelled" -> "This event has been cancelled by the organizer."
            "announcement" -> "New announcement from the organizer."
            else -> "Open EventRoute to view details."
        }

        return Pair(defaultTitle, defaultBody)
    }

    fun buildNotification(
        context: Context,
        type: String,
        eventId: String? = null,
        teamId: String? = null,
        title: String? = null,
        body: String? = null
    ): Notification? {
        val channel = channelForType(type) ?: return null
        val deepLinkUri = deepLinkFor(type, eventId, teamId) ?: return null
        val (finalTitle, finalBody) = defaultTitleAndBody(type, title, body)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri), context, MainActivity::class.java)

        val requestCode = (eventId ?: teamId ?: type).hashCode()
        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntent(mainIntent)
            addNextIntent(deepLinkIntent)
            getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_eventroute)
            .setContentTitle(finalTitle)
            .setContentText(finalBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(finalBody))
            .setPriority(
                when (channel.importance) {
                    NotificationManager.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
                    NotificationManager.IMPORTANCE_DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showNotification(
        context: Context,
        type: String,
        eventId: String? = null,
        teamId: String? = null,
        title: String? = null,
        body: String? = null,
        settings: NotificationSettings? = null,
        nowTime: LocalTime = LocalTime.now(),
        notificationId: Int = (eventId ?: teamId ?: type).hashCode()
    ): Boolean {
        if (settings != null && !shouldAllowNotification(type, settings, nowTime)) {
            return false
        }

        val notification = buildNotification(context, type, eventId, teamId, title, body) ?: return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        manager.notify(notificationId, notification)
        return true
    }
}
