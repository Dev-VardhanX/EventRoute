package com.eventroute.app

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.dto.EventListResponse
import com.eventroute.app.domain.live.LiveFeedSimulator
import com.eventroute.app.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class EventQueryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var allEvents: List<Event>

    private fun loadMockJson(name: String): String {
        val candidates = listOf(
            File("src/main/assets/mock/$name"),
            File("app/src/main/assets/mock/$name"),
            File("../app/src/main/assets/mock/$name")
        )
        for (f in candidates) {
            if (f.exists()) return f.readText()
        }
        throw IllegalStateException("Could not find mock file: $name")
    }

    @Before
    fun setup() {
        val raw = loadMockJson("events.json")
        val response = json.decodeFromString<EventListResponse>(raw)
        allEvents = response.items.map { it.toDomain() }
    }

    private fun applyQuery(events: List<Event>, query: EventQuery): List<Event> {
        var items = events

        // Category filter
        if (query.category.isNotBlank() && !query.category.equals("All", true) && !query.category.equals("All Events", true)) {
            items = items.filter { it.category.contains(query.category.removeSuffix("s"), ignoreCase = true) }
        }

        // Participation filter
        when (query.participation) {
            ParticipationFilter.SOLO -> items = items.filter { it.participationType.equals("solo", true) }
            ParticipationFilter.TEAM -> items = items.filter { it.participationType.equals("team", true) }
            ParticipationFilter.EITHER -> items = items.filter { it.participationType.equals("either", true) }
            ParticipationFilter.ALL -> {}
        }

        // Free / Paid filter
        when (query.feeFilter) {
            FeeFilter.FREE -> items = items.filter { it.isFree }
            FeeFilter.PAID -> items = items.filter { !it.isFree }
            FeeFilter.ALL -> {}
        }

        // Search text
        if (query.text.isNotBlank()) {
            val q = query.text.trim().lowercase()
            items = items.filter {
                it.title.lowercase().contains(q) ||
                it.collegeName.lowercase().contains(q) ||
                it.venueName.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }

        // Distance
        if (query.userLat != null && query.userLng != null) {
            val userPoint = GeoPoint(query.userLat, query.userLng)
            items = items.map { event ->
                val distKm = LiveFeedSimulator.haversineM(userPoint, GeoPoint(event.venueLat, event.venueLng)) / 1000.0
                event.copy(distanceKm = Math.round(distKm * 10.0) / 10.0)
            }
            if (query.maxDistanceKm != null) {
                items = items.filter { (it.distanceKm ?: Double.MAX_VALUE) <= query.maxDistanceKm }
            }
        }

        // Max travel time
        if (query.maxTravelMin != null) {
            items = items.filter {
                val travelMin = it.smartTravelMinutes ?: it.travelEstimateMinutes ?: 0
                travelMin <= query.maxTravelMin
            }
        }

        // Sorting
        return when (query.sort) {
            SortOrder.SOONEST -> items.sortedBy { it.startAt }
            SortOrder.NEAREST -> items.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            SortOrder.SHORTEST_TRAVEL_TIME -> items.sortedBy { it.smartTravelMinutes ?: it.travelEstimateMinutes ?: Int.MAX_VALUE }
        }
    }

    @Test
    fun testSearchQueryText() {
        val resultHackX = applyQuery(allEvents, EventQuery(text = "HackX"))
        assertEquals(1, resultHackX.size)
        assertEquals("HackX 2024", resultHackX[0].title)

        val resultRobo = applyQuery(allEvents, EventQuery(text = "RoboCup"))
        assertEquals(1, resultRobo.size)
        assertEquals("RoboCup Season 5", resultRobo[0].title)
    }

    @Test
    fun testCategoryFilter() {
        val hackathons = applyQuery(allEvents, EventQuery(category = "Hackathons"))
        assertTrue(hackathons.isNotEmpty())
        assertTrue(hackathons.all { it.category.contains("Hackathon", ignoreCase = true) })

        val robotics = applyQuery(allEvents, EventQuery(category = "Robotics"))
        assertTrue(robotics.isNotEmpty())
        assertTrue(robotics.all { it.category.contains("Robotics", ignoreCase = true) })
    }

    @Test
    fun testParticipationFilter() {
        val teamEvents = applyQuery(allEvents, EventQuery(participation = ParticipationFilter.TEAM))
        assertTrue(teamEvents.all { it.participationType.equals("team", ignoreCase = true) })
    }

    @Test
    fun testFreeAndPaidFilter() {
        val freeEvents = applyQuery(allEvents, EventQuery(feeFilter = FeeFilter.FREE))
        assertTrue(freeEvents.all { it.isFree })

        val paidEvents = applyQuery(allEvents, EventQuery(feeFilter = FeeFilter.PAID))
        assertTrue(paidEvents.all { !it.isFree })
    }

    @Test
    fun testDistanceFilter() {
        // User at Dwarka NSUT
        val userLat = 28.6094
        val userLng = 77.0368

        val nearby = applyQuery(allEvents, EventQuery(userLat = userLat, userLng = userLng, maxDistanceKm = 15.0))
        assertTrue(nearby.isNotEmpty())
        assertTrue(nearby.all { (it.distanceKm ?: 0.0) <= 15.0 })
    }

    @Test
    fun testSortOrders() {
        val userLat = 28.6094
        val userLng = 77.0368

        // Soonest sort
        val soonest = applyQuery(allEvents, EventQuery(sort = SortOrder.SOONEST))
        for (i in 0 until soonest.size - 1) {
            assertTrue(soonest[i].startAt <= soonest[i + 1].startAt)
        }

        // Nearest sort
        val nearest = applyQuery(allEvents, EventQuery(userLat = userLat, userLng = userLng, sort = SortOrder.NEAREST))
        for (i in 0 until nearest.size - 1) {
            assertTrue((nearest[i].distanceKm ?: 0.0) <= (nearest[i + 1].distanceKm ?: Double.MAX_VALUE))
        }

        // Shortest travel time
        val shortestTravel = applyQuery(allEvents, EventQuery(sort = SortOrder.SHORTEST_TRAVEL_TIME))
        for (i in 0 until shortestTravel.size - 1) {
            val a = shortestTravel[i].smartTravelMinutes ?: shortestTravel[i].travelEstimateMinutes ?: 0
            val b = shortestTravel[i + 1].smartTravelMinutes ?: shortestTravel[i + 1].travelEstimateMinutes ?: 0
            assertTrue(a <= b)
        }
    }
}
