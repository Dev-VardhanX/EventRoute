package com.eventroute.app

import com.eventroute.app.notifications.NotificationSettings
import com.eventroute.app.notifications.isInQuietHours
import com.eventroute.app.notifications.shouldAllowNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class QuietHoursTest {

    @Test
    fun testWindowCrossingMidnight_2200_to_0700() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)

        // Inside quiet hours (cross midnight)
        assertTrue(isInQuietHours(LocalTime.of(22, 0), start, end))
        assertTrue(isInQuietHours(LocalTime.of(23, 30), start, end))
        assertTrue(isInQuietHours(LocalTime.of(0, 0), start, end))
        assertTrue(isInQuietHours(LocalTime.of(4, 15), start, end))
        assertTrue(isInQuietHours(LocalTime.of(6, 59), start, end))

        // Outside quiet hours
        assertFalse(isInQuietHours(LocalTime.of(7, 0), start, end))
        assertFalse(isInQuietHours(LocalTime.of(12, 0), start, end))
        assertFalse(isInQuietHours(LocalTime.of(21, 59), start, end))
    }

    @Test
    fun testWindowSameDay_1400_to_1600() {
        val start = LocalTime.of(14, 0)
        val end = LocalTime.of(16, 0)

        assertTrue(isInQuietHours(LocalTime.of(14, 0), start, end))
        assertTrue(isInQuietHours(LocalTime.of(15, 0), start, end))
        assertFalse(isInQuietHours(LocalTime.of(16, 0), start, end))
        assertFalse(isInQuietHours(LocalTime.of(13, 59), start, end))
    }

    @Test
    fun testQuietHoursBypass_leaveNowAndEventCancelled() {
        val settings = NotificationSettings(
            quietHoursEnabled = true,
            quietHoursStart = LocalTime.of(22, 0),
            quietHoursEnd = LocalTime.of(7, 0)
        )
        val midnight = LocalTime.of(0, 30) // strictly inside quiet hours

        // Critical alerts MUST bypass quiet hours
        assertTrue(shouldAllowNotification("leave_now", settings, midnight))
        assertTrue(shouldAllowNotification("event_cancelled", settings, midnight))

        // Non-critical alerts MUST be suppressed during quiet hours
        assertFalse(shouldAllowNotification("leave_soon", settings, midnight))
        assertFalse(shouldAllowNotification("event_reminder", settings, midnight))
        assertFalse(shouldAllowNotification("team_member_joined", settings, midnight))
        assertFalse(shouldAllowNotification("announcement", settings, midnight))
        assertFalse(shouldAllowNotification("teammate_delayed", settings, midnight))
    }

    @Test
    fun testGroupTogglesRespected() {
        val noon = LocalTime.of(12, 0)

        // Reminders disabled
        val noReminders = NotificationSettings(remindersEnabled = false)
        assertFalse(shouldAllowNotification("leave_soon", noReminders, noon))
        assertFalse(shouldAllowNotification("leave_now", noReminders, noon))
        assertFalse(shouldAllowNotification("event_reminder", noReminders, noon))

        // Team updates disabled
        val noTeam = NotificationSettings(teamEnabled = false)
        assertFalse(shouldAllowNotification("team_member_joined", noTeam, noon))
        assertFalse(shouldAllowNotification("team_confirmed", noTeam, noon))
        assertFalse(shouldAllowNotification("join_suggestion", noTeam, noon))

        // Alerts disabled
        val noAlerts = NotificationSettings(alertsEnabled = false)
        assertFalse(shouldAllowNotification("teammate_delayed", noAlerts, noon))
        assertFalse(shouldAllowNotification("event_updated", noAlerts, noon))

        // Announcements disabled
        val noAnnouncements = NotificationSettings(announcementsEnabled = false)
        assertFalse(shouldAllowNotification("announcement", noAnnouncements, noon))
    }
}
