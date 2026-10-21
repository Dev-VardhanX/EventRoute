package com.eventroute.app.di

import android.content.Context
import com.eventroute.app.data.local.AppDatabase
import com.eventroute.app.data.local.PersistentAppDatabase

/**
 * Dependency injection module providing local storage and database singletons.
 */
object DatabaseModule {
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun getContext(): Context =
        appContext ?: throw IllegalStateException("DatabaseModule must be initialized with Context")

    val database: AppDatabase by lazy {
        PersistentAppDatabase(getContext())
    }
}
