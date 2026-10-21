package com.eventroute.app

import com.eventroute.app.data.local.InMemoryAppDatabase
import com.eventroute.app.domain.model.College
import com.eventroute.app.domain.model.Event
import com.eventroute.app.notifications.NotificationSettings
import com.eventroute.app.domain.repository.EventsRepository
import com.eventroute.app.domain.repository.SavedEventsRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SavedEventsTest {

    private class FakeEventsRepository(private val events: List<Event>) : EventsRepository {
        override suspend fun getEvents(category: String?, searchQuery: String?, participation: String?): Result<List<Event>> =
            Result.success(events)

        override suspend fun getEventById(id: String): Result<Event> =
            events.find { it.id == id }?.let { Result.success(it) } ?: Result.failure(NoSuchElementException())

        override suspend fun getTravelEstimates(originLat: Double, originLng: Double, eventIds: List<String>): Result<Map<String, Int>> =
            Result.success(emptyMap())
    }

    private val testEvent1 = Event(
        id = "ev-1",
        title = "HackX",
        organizerName = "Tech Club",
        college = College("c1", "IIT Delhi"),
        category = "Hackathons",
        startAt = "2026-10-24T04:30:00Z",
        checkInAt = "2026-10-24T03:30:00Z",
        registrationDeadline = "2026-10-23T18:30:00Z",
        venueName = "Bharti Building",
        venueLat = 28.5459,
        venueLng = 77.1926,
        participationType = "team",
        seatLimit = 100,
        seatsLeft = 50
    )

    private val testEvent2 = Event(
        id = "ev-2",
        title = "RoboCup",
        organizerName = "Robotics Club",
        college = College("c2", "DTU"),
        category = "Robotics",
        startAt = "2026-10-28T04:30:00Z",
        checkInAt = "2026-10-28T03:30:00Z",
        registrationDeadline = "2026-10-26T18:30:00Z",
        venueName = "Main Auditorium",
        venueLat = 28.7501,
        venueLng = 77.1177,
        participationType = "solo",
        seatLimit = 80,
        seatsLeft = 20
    )

    @Test
    fun testSavedEventsToggleAndFilter() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val inMemoryDb = InMemoryAppDatabase()
        val fakeEventsRepo = FakeEventsRepository(listOf(testEvent1, testEvent2))

        val repo = SavedEventsRepositoryImpl(
            localDb = inMemoryDb,
            eventsRepository = fakeEventsRepo,
            scope = testScope
        )

        // Initially no events saved
        assertFalse(repo.isSaved("ev-1"))
        val initialSaved = repo.getSavedEvents().getOrThrow()
        assertTrue(initialSaved.isEmpty())

        // Save ev-1
        val toggledOn = repo.toggle("ev-1")
        assertTrue(toggledOn)
        assertTrue(repo.isSaved("ev-1"))

        val savedList = repo.getSavedEvents().getOrThrow()
        assertEquals(1, savedList.size)
        assertEquals("ev-1", savedList[0].id)

        // Toggle ev-1 off
        val toggledOff = repo.toggle("ev-1")
        assertFalse(toggledOff)
        assertFalse(repo.isSaved("ev-1"))

        val emptyAgain = repo.getSavedEvents().getOrThrow()
        assertTrue(emptyAgain.isEmpty())
    }

    @Test
    fun testNotificationSettingsModelDefaults() {
        val defaults = com.eventroute.app.notifications.NotificationSettings()

        assertTrue(defaults.remindersEnabled)
        assertTrue(defaults.teamEnabled)
        assertTrue(defaults.alertsEnabled)
        assertTrue(defaults.announcementsEnabled)
        assertFalse(defaults.quietHoursEnabled)
        assertEquals(LocalTime.of(22, 0), defaults.quietHoursStart)
        assertEquals(LocalTime.of(7, 0), defaults.quietHoursEnd)
    }
}
