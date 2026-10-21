package com.eventroute.app.data.repository.mock

import android.content.Context
import com.eventroute.app.data.local.AppDatabase
import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.mock.AssetReader
import com.eventroute.app.data.remote.dto.EventListResponse
import com.eventroute.app.data.remote.dto.RegistrationListResponse
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.repository.RegistrationRepository
import kotlinx.coroutines.delay

class MockRegistrationRepository(
    private val context: Context,
    private val localDb: AppDatabase
) : RegistrationRepository {

    private suspend fun seedIfEmpty() {
        val existing = localDb.getRegisteredEvents()
        if (existing.isEmpty()) {
            val response: RegistrationListResponse = AssetReader.readJsonAsset(context, "mock/registrations.json")
            val events = response.items.mapNotNull { reg ->
                reg.event?.toDomain()?.copy(
                    ticketToken = reg.ticketToken ?: "evrt.${reg.eventId}.${reg.userId}.8f3a1c",
                    ticketCode = "#EVRT-${reg.eventId.takeLast(4).uppercase()}-X90"
                )
            }
            events.forEach { localDb.saveRegistration(it) }
        }
    }

    override suspend fun registerSolo(eventId: String): Result<Unit> {
        return runCatching {
            com.eventroute.app.debug.DebugFlags.checkAndSimulate("registration")
            delay(300)
            seedIfEmpty()
            val eventsResponse: EventListResponse = AssetReader.readJsonAsset(context, "mock/events.json")
            val event = eventsResponse.items.find { it.id == eventId }?.toDomain()
                ?: throw NoSuchElementException("Event not found")
            val token = "evrt.$eventId.u-john.8f3a1c"
            val withTicket = event.copy(
                ticketToken = token,
                ticketCode = "#EVRT-${eventId.takeLast(4).uppercase()}-X90"
            )
            localDb.saveRegistration(withTicket)
            runCatching {
                com.eventroute.app.di.AppModule.reminderScheduler.scheduleEventReminder(
                    eventId = withTicket.id,
                    title = withTicket.title,
                    startAt = java.time.Instant.parse(withTicket.startAt)
                )
            }
        }
    }

    override suspend fun getMyRegistrations(): Result<List<Event>> {
        delay(300)
        return runCatching {
            seedIfEmpty()
            val list = localDb.getRegisteredEvents()
            list
        }
    }

    override suspend fun cancelRegistration(registrationOrEventId: String): Result<Unit> {
        delay(300)
        return runCatching {
            localDb.removeRegistration(registrationOrEventId)
            com.eventroute.app.di.AppModule.reminderScheduler.cancelFor(registrationOrEventId)
        }
    }
}
