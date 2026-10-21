package com.eventroute.app

import android.app.Application
import com.eventroute.app.di.AppModule

class EventRouteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
        com.eventroute.app.notifications.NotificationChannels.createAll(this)
    }
}
