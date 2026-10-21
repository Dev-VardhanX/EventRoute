package com.eventroute.app

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.dto.EventListResponse
import com.eventroute.app.domain.model.Event
import com.eventroute.app.domain.model.EventQuery
import com.eventroute.app.domain.repository.EventsRepository
import com.eventroute.app.domain.repository.SavedEventsRepository
import com.eventroute.app.ui.components.UiState
import com.eventroute.app.ui.screens.eventdetails.EventDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailsRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadMockJson(name: String): String {
        val candidates = listOf(
            File("src/main/assets/mock/$name"),
            File("app/src/main/assets/mock/$name"),
            File("../app/src/main/assets/mock/$name")
        )
        for (f in candidates) {
            if (f.exists()) return f.readText()
        }
        throw IllegalStateException("Could not find mock file: $name in candidates: $candidates")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestEventsRepository(private val events: List<Event>) : EventsRepository {
        override suspend fun getEvents(category: String?, searchQuery: String?, participation: String?): Result<List<Event>> =
            Result.success(events)

        override suspend fun queryEvents(query: EventQuery): Result<List<Event>> =
            Result.success(events)

        override suspend fun getEventById(id: String): Result<Event> {
            val event = events.find { it.id == id }
                ?: return Result.failure(NoSuchElementException("Event not found with id: $id"))
            return Result.success(event)
        }

        override suspend fun getTravelEstimates(originLat: Double, originLng: Double, eventIds: List<String>): Result<Map<String, Int>> =
            Result.success(eventIds.associateWith { 30 })
    }

    private class TestSavedEventsRepository : SavedEventsRepository {
        private val _ids = MutableStateFlow<Set<String>>(emptySet())
        override val savedEventIds: StateFlow<Set<String>> = _ids.asStateFlow()
        override suspend fun toggle(eventId: String): Boolean = true
        override suspend fun isSaved(eventId: String): Boolean = false
        override suspend fun getSavedEvents(): Result<List<Event>> = Result.success(emptyList())
    }

    @Test
    fun testEveryEventLoadsSuccessfullyThroughViewModel() = runTest(testDispatcher) {
        val jsonString = loadMockJson("events.json")
        val response = json.decodeFromString<EventListResponse>(jsonString)
        val domainEvents = response.items.map { it.toDomain() }

        assertTrue("events.json must have items", domainEvents.isNotEmpty())
        val repository = TestEventsRepository(domainEvents)
        val savedRepo = TestSavedEventsRepository()

        for (expectedEvent in domainEvents) {
            val viewModel = EventDetailsViewModel(
                eventsRepository = repository,
                savedEventsRepository = savedRepo
            )

            viewModel.loadEvent(expectedEvent.id)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(
                "Event ${expectedEvent.id} should load successfully, got: $state",
                state is UiState.Success
            )
            val loaded = (state as UiState.Success).data
            assertEquals(expectedEvent.id, loaded.id)
            assertEquals(expectedEvent.title, loaded.title)
            assertEquals(expectedEvent.venueLat, loaded.venueLat, 0.0001)
            assertEquals(expectedEvent.venueLng, loaded.venueLng, 0.0001)
            assertTrue("Title should not be blank", loaded.title.isNotBlank())
            assertTrue("College name should not be blank", loaded.collegeName.isNotBlank())
        }
    }

    @Test
    fun testUnknownEventIdShowsEmptyStateWithoutCrashing() = runTest(testDispatcher) {
        val jsonString = loadMockJson("events.json")
        val response = json.decodeFromString<EventListResponse>(jsonString)
        val domainEvents = response.items.map { it.toDomain() }

        val repository = TestEventsRepository(domainEvents)
        val viewModel = EventDetailsViewModel(
            eventsRepository = repository,
            savedEventsRepository = TestSavedEventsRepository()
        )

        viewModel.loadEvent("non-existent-uuid-1234")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Unknown ID should transition to UiState.Empty, got: $state", state is UiState.Empty)
    }

    @Test
    fun testBlankEventIdShowsEmptyStateWithoutCrashing() = runTest(testDispatcher) {
        val repository = TestEventsRepository(emptyList())
        val viewModel = EventDetailsViewModel(
            eventsRepository = repository,
            savedEventsRepository = TestSavedEventsRepository()
        )

        viewModel.loadEvent("")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Blank ID should transition to UiState.Empty, got: $state", state is UiState.Empty)
    }
}
