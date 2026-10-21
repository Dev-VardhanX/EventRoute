package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.Event

interface RegistrationRepository {
    suspend fun registerSolo(eventId: String): Result<Unit>
    suspend fun getMyRegistrations(): Result<List<Event>>
    suspend fun cancelRegistration(registrationOrEventId: String): Result<Unit>
}
