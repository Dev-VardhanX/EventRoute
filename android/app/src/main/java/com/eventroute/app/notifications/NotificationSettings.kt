package com.eventroute.app.notifications

import java.time.LocalTime

data class NotificationSettings(
    val remindersEnabled: Boolean = true,
    val teamEnabled: Boolean = true,
    val alertsEnabled: Boolean = true,
    val announcementsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0)
)

/**
 * Checks whether [now] falls within the quiet hours window [quietStart, quietEnd).
 * Correctly handles windows that span across midnight (e.g. 22:00 to 07:00).
 */
fun isInQuietHours(now: LocalTime, quietStart: LocalTime, quietEnd: LocalTime): Boolean {
    return if (quietStart <= quietEnd) {
        now >= quietStart && now < quietEnd
    } else {
        // Window crosses midnight, e.g. 22:00 to 07:00
        now >= quietStart || now < quietEnd
    }
}

/**
 * Determines whether a notification of [type] should be displayed given [settings] and [nowTime].
 * Quiet hours suppress everything EXCEPT "leave_now" and "event_cancelled".
 */
fun shouldAllowNotification(
    type: String,
    settings: NotificationSettings,
    nowTime: LocalTime = LocalTime.now()
): Boolean {
    val channel = NotificationRouter.channelForType(type) ?: return false

    val channelAllowed = when (channel) {
        NotifChannel.REMINDERS -> settings.remindersEnabled
        NotifChannel.TEAM -> settings.teamEnabled
        NotifChannel.ALERTS -> settings.alertsEnabled
        NotifChannel.ANNOUNCEMENTS -> settings.announcementsEnabled
        NotifChannel.JOURNEY -> true
    }
    if (!channelAllowed) return false

    if (settings.quietHoursEnabled && isInQuietHours(nowTime, settings.quietHoursStart, settings.quietHoursEnd)) {
        if (type != "leave_now" && type != "event_cancelled") {
            return false
        }
    }

    return true
}
