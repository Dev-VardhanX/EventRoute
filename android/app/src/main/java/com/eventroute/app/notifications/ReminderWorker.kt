package com.eventroute.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eventroute.app.di.AppModule

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: return Result.failure()
        val eventId = inputData.getString("event_id")
        val title = inputData.getString("title")

        val settingsRepo = AppModule.notificationSettingsRepository
        val settings = settingsRepo.getSettings()

        NotificationRouter.showNotification(
            context = applicationContext,
            type = type,
            eventId = eventId,
            title = title,
            settings = settings
        )

        return Result.success()
    }
}
