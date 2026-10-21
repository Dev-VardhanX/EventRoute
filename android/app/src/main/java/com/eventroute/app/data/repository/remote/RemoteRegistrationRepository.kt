package com.eventroute.app.data.repository.remote

import com.eventroute.app.data.local.AppDatabase
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.api.RegistrationApi
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.repository.RegistrationRepository

class RemoteRegistrationRepository(
    private val registrationApi: RegistrationApi,
    private val localDb: AppDatabase
) : RegistrationRepository {

    override suspend fun registerSolo(eventId: String): Result<Unit> = runCatching {
        val dto = registrationApi.registerSolo(eventId)
        dto.event?.toDomain()?.let { localDb.saveRegistration(it) }
        Unit
    }

    override suspend fun getMyRegistrations(): Result<List<Event>> = runCatching {
        try {
            val response = registrationApi.getMyRegistrations()
            val events = response.items.mapNotNull { it.event?.toDomain() }
            events.forEach { localDb.saveRegistration(it) }
            events
        } catch (e: Exception) {
            // Offline fallback to local Room cache
            val cached = localDb.getRegisteredEvents()
            if (cached.isNotEmpty()) cached else throw e
        }
    }

    override suspend fun cancelRegistration(registrationOrEventId: String): Result<Unit> = runCatching {
        registrationApi.cancelRegistration(registrationOrEventId)
        localDb.removeRegistration(registrationOrEventId)
        Unit
    }
}
