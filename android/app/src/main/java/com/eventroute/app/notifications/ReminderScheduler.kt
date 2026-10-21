package com.eventroute.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.Instant

data class ScheduledReminder(
    val type: String,
    val time: Instant
)

/**
 * Pure function returning leave-soon (leaveBy minus 15 min) and leave-now (leaveBy).
 * Any times strictly in the past relative to [now] are dropped.
 */
fun notificationTimes(leaveBy: Instant, now: Instant): List<ScheduledReminder> {
    val result = mutableListOf<ScheduledReminder>()
    val leaveSoon = leaveBy.minus(Duration.ofMinutes(15))
    if (!leaveSoon.isBefore(now)) {
        result.add(ScheduledReminder("leave_soon", leaveSoon))
    }
    if (!leaveBy.isBefore(now)) {
        result.add(ScheduledReminder("leave_now", leaveBy))
    }
    return result
}

class ReminderScheduler(private val ctx: Context) {

    fun scheduleLeaveAlerts(eventId: String, title: String, leaveBy: Instant) {
        val now = Instant.now()
        val times = notificationTimes(leaveBy, now)
        for (item in times) {
            enqueue(item.type, eventId, title, item.time)
        }
    }

    fun scheduleEventReminder(eventId: String, title: String, startAt: Instant) {
        val reminderTime = startAt.minus(Duration.ofHours(24))
        if (!reminderTime.isBefore(Instant.now())) {
            enqueue("event_reminder", eventId, title, reminderTime)
        }
    }

    fun cancelFor(eventId: String) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag("event:$eventId")
    }

    fun enqueue(type: String, eventId: String, title: String, at: Instant) {
        val delay = Duration.between(Instant.now(), at)
        if (delay.isNegative) return
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf("type" to type, "event_id" to eventId, "title" to title))
            .addTag("event:$eventId")
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("$type:$eventId", ExistingWorkPolicy.REPLACE, req)
    }

    fun scheduleDebugTrigger(type: String, eventId: String, title: String, inSeconds: Long = 10) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.ofSeconds(inSeconds))
            .setInputData(workDataOf("type" to type, "event_id" to eventId, "title" to title))
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }
}
