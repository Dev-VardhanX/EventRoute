package com.eventroute.app

import com.eventroute.app.notifications.NotifChannel
import com.eventroute.app.notifications.NotificationRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRouterTest {

    @Test
    fun testChannelMappings() {
        // Reminders channel
        assertEquals(NotifChannel.REMINDERS, NotificationRouter.channelForType("leave_soon"))
        assertEquals(NotifChannel.REMINDERS, NotificationRouter.channelForType("leave_now"))
        assertEquals(NotifChannel.REMINDERS, NotificationRouter.channelForType("event_reminder"))
        assertEquals(NotifChannel.REMINDERS, NotificationRouter.channelForType("deadline_reminder"))

        // Team channel
        assertEquals(NotifChannel.TEAM, NotificationRouter.channelForType("registration_confirmed"))
        assertEquals(NotifChannel.TEAM, NotificationRouter.channelForType("team_member_joined"))
        assertEquals(NotifChannel.TEAM, NotificationRouter.channelForType("team_member_left"))
        assertEquals(NotifChannel.TEAM, NotificationRouter.channelForType("team_confirmed"))
        assertEquals(NotifChannel.TEAM, NotificationRouter.channelForType("team_incomplete"))
        assertEquals(NotifChannel.TEAM, NotificationRouter.channelForType("join_suggestion"))

        // Alerts channel
        assertEquals(NotifChannel.ALERTS, NotificationRouter.channelForType("teammate_delayed"))
        assertEquals(NotifChannel.ALERTS, NotificationRouter.channelForType("team_late_risk"))
        assertEquals(NotifChannel.ALERTS, NotificationRouter.channelForType("event_updated"))
        assertEquals(NotifChannel.ALERTS, NotificationRouter.channelForType("event_cancelled"))

        // Announcements channel
        assertEquals(NotifChannel.ANNOUNCEMENTS, NotificationRouter.channelForType("announcement"))

        // Unknown type is safely ignored
        assertNull(NotificationRouter.channelForType("unknown_random_type"))
    }

    @Test
    fun testDeepLinkMappings() {
        val eventId = "ev-12345"
        val teamId = "team-squad"

        // Opens event screen
        val eventScreens = listOf(
            "registration_confirmed", "event_reminder", "deadline_reminder",
            "event_updated", "event_cancelled", "announcement"
        )
        for (type in eventScreens) {
            val link = NotificationRouter.deepLinkFor(type, eventId, teamId)
            assertEquals("eventroute://event/$eventId", link)
        }

        // Opens journey screen
        val journeyScreens = listOf("leave_soon", "leave_now")
        for (type in journeyScreens) {
            val link = NotificationRouter.deepLinkFor(type, eventId, teamId)
            assertEquals("eventroute://journey/$eventId", link)
        }

        // Opens team plan screen
        val teamScreens = listOf(
            "team_member_joined", "team_member_left", "team_confirmed",
            "team_incomplete", "join_suggestion", "teammate_delayed", "team_late_risk"
        )
        for (type in teamScreens) {
            val link = NotificationRouter.deepLinkFor(type, eventId, teamId)
            assertEquals("eventroute://team/$teamId/plan", link)
        }

        // Unknown type produces null deep link safely
        assertNull(NotificationRouter.deepLinkFor("unknown_type", eventId, teamId))
    }
}
