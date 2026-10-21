package com.eventroute.app

import com.eventroute.app.domain.model.College
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.model.EventQuery
import com.eventroute.app.domain.repository.EventsRepository
import com.eventroute.app.ui.components.UiState
import com.eventroute.app.ui.navigation.Screen
import com.eventroute.app.ui.screens.venuemap.VenueMapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VenueMapTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleCollege = College(
        id = "col-1",
        name = "IIT Delhi",
        city = "Delhi",
        lat = 28.5449,
        lng = 77.1926
    )

    private val sampleEventWithCoords = Event(
        id = "event-with-coords",
        title = "Hackathon 2026",
        organizerName = "Tech Club",
        college = sampleCollege,
        category = "hackathon",
        startAt = "2026-10-24T09:00:00Z",
        checkInAt = "2026-10-24T08:30:00Z",
        registrationDeadline = "2026-10-23T18:00:00Z",
        venueName = "Bharti Building",
        venueLat = 28.5449,
        venueLng = 77.1926,
        participationType = "team",
        seatLimit = 100,
        seatsLeft = 58,
        feeAmount = 0.0
    )

    private val sampleEventZeroCoords = sampleEventWithCoords.copy(
        id = "event-zero-coords",
        venueLat = 0.0,
        venueLng = 0.0
    )

    private class TestEventsRepository(
        private val events: Map<String, Event>
    ) : EventsRepository {
        override suspend fun getEvents(category: String?, searchQuery: String?, participation: String?): Result<List<Event>> =
            Result.success(events.values.toList())

        override suspend fun queryEvents(query: EventQuery): Result<List<Event>> =
            Result.success(events.values.toList())

        override suspend fun getEventById(id: String): Result<Event> {
            val event = events[id]
            return if (event != null) {
                Result.success(event)
            } else {
                Result.failure(NoSuchElementException("Event not found with id: $id"))
            }
        }

        override suspend fun getEvent(id: String): Result<Event> = getEventById(id)

        override suspend fun getTravelEstimates(
            originLat: Double,
            originLng: Double,
            eventIds: List<String>
        ): Result<Map<String, Int>> = Result.success(emptyMap())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRouteBuilder() {
        assertEquals("venue_map/{eventId}", Screen.VenueMap.route)
        assertEquals("venue_map/hackx-2024", Screen.VenueMap.createRoute("hackx-2024"))
        assertEquals(
            "venue_map/11111111-1111-1111-1111-111111111111",
            Screen.VenueMap.createRoute("11111111-1111-1111-1111-111111111111")
        )
    }

    @Test
    fun testVenueMapViewModel_successWithValidCoordinates() = runTest(testDispatcher) {
        val repo = TestEventsRepository(
            mapOf("event-with-coords" to sampleEventWithCoords)
        )
        val vm = VenueMapViewModel(eventsRepository = repo)

        vm.loadVenue("event-with-coords")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected UiState.Success but was $state", state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals("event-with-coords", data.id)
        assertEquals(28.5449, data.venueLat, 0.0001)
        assertEquals(77.1926, data.venueLng, 0.0001)
        assertEquals("Bharti Building", data.venueName)
    }

    @Test
    fun testVenueMapViewModel_unknownIdReturnsEmpty() = runTest(testDispatcher) {
        val repo = TestEventsRepository(
            mapOf("event-with-coords" to sampleEventWithCoords)
        )
        val vm = VenueMapViewModel(eventsRepository = repo)

        vm.loadVenue("non-existent-id")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UiState.Empty, state)
    }

    @Test
    fun testVenueMapViewModel_blankIdReturnsEmpty() = runTest(testDispatcher) {
        val repo = TestEventsRepository(emptyMap())
        val vm = VenueMapViewModel(eventsRepository = repo)

        vm.loadVenue("")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UiState.Empty, state)
    }

    @Test
    fun testVenueMapViewModel_zeroCoordinatesReturnsEmpty() = runTest(testDispatcher) {
        val repo = TestEventsRepository(
            mapOf("event-zero-coords" to sampleEventZeroCoords)
        )
        val vm = VenueMapViewModel(eventsRepository = repo)

        vm.loadVenue("event-zero-coords")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UiState.Empty, state)
    }
}
