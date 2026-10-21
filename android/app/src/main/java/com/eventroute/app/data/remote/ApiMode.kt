package com.eventroute.app.data.remote

import com.eventroute.app.BuildConfig

object ApiMode {
    fun isMock(
        group: String,
        useMock: Boolean = BuildConfig.USE_MOCK,
        liveEndpoints: String = BuildConfig.LIVE_ENDPOINTS
    ): Boolean {
        val liveSet = liveEndpoints.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return useMock && group.lowercase() !in liveSet
    }
}
