package com.eventroute.app

import com.eventroute.app.notifications.notificationTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReminderScheduleTest {

    @Test
    fun testNotificationTimes_bothFuture() {
        val now = Instant.parse("2026-10-24T00:00:00Z")
        val leaveBy = Instant.parse("2026-10-24T02:00:00Z") // 2 hours in the future

        val times = notificationTimes(leaveBy, now)
        assertEquals(2, times.size)

        val leaveSoon = times.find { it.type == "leave_soon" }
        val leaveNow = times.find { it.type == "leave_now" }

        assertEquals(leaveBy.minus(Duration.ofMinutes(15)), leaveSoon?.time)
        assertEquals(leaveBy, leaveNow?.time)
    }

    @Test
    fun testNotificationTimes_leaveSoonInPast_leaveNowInFuture() {
        // leaveBy is in 10 minutes: leaveSoon (leaveBy - 15m) is in the past!
        val now = Instant.parse("2026-10-24T01:50:00Z")
        val leaveBy = Instant.parse("2026-10-24T02:00:00Z")

        val times = notificationTimes(leaveBy, now)
        assertEquals(1, times.size)
        assertEquals("leave_now", times[0].type)
        assertEquals(leaveBy, times[0].time)
    }

    @Test
    fun testNotificationTimes_bothInPast_dropped() {
        // Both leaveSoon and leaveNow are in the past
        val now = Instant.parse("2026-10-24T02:05:00Z")
        val leaveBy = Instant.parse("2026-10-24T02:00:00Z")

        val times = notificationTimes(leaveBy, now)
        assertTrue(times.isEmpty())
    }
}
