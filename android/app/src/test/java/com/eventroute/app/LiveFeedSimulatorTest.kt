package com.eventroute.app

import com.eventroute.app.domain.live.LiveFeedSimulator
import com.eventroute.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveFeedSimulatorTest {

    private val route = LiveFeedSimulator.DEFAULT_ROUTE

    @Before
    fun setup() {
        LiveFeedSimulator.restartSimulation()
    }

    @Test
    fun testPointAt_startMiddleEnd() {
        assertTrue(route.isNotEmpty())

        val start = LiveFeedSimulator.pointAt(route, 0.0)
        assertEquals(route.first().lat, start.lat, 0.0001)
        assertEquals(route.first().lng, start.lng, 0.0001)

        val end = LiveFeedSimulator.pointAt(route, 1.0)
        assertEquals(route.last().lat, end.lat, 0.0001)
        assertEquals(route.last().lng, end.lng, 0.0001)

        val middle = LiveFeedSimulator.pointAt(route, 0.5)
        // Middle point must be somewhere between start and end
        val startDist = LiveFeedSimulator.haversineM(start, middle)
        val endDist = LiveFeedSimulator.haversineM(middle, end)
        val totalDist = LiveFeedSimulator.haversineM(start, end)
        assertTrue(startDist > 0.0)
        assertTrue(endDist > 0.0)
    }

    @Test
    fun testAlexMovesSlowerAndEndsUpLater() {
        // Run 5 ticks of the simulation
        repeat(5) {
            LiveFeedSimulator.tick()
        }

        val alexFrac = LiveFeedSimulator.getAlexFraction()
        val sarahFrac = LiveFeedSimulator.getSarahFraction()
        val youFrac = LiveFeedSimulator.getYouFraction()

        // Alex speed is 0.6x, starts behind, and remains behind
        assertTrue(alexFrac < sarahFrac)
        assertTrue(sarahFrac < youFrac)

        val state = LiveFeedSimulator.liveState.value
        val alexMember = state.members.find { it.userId == "u3" }
        val sarahMember = state.members.find { it.userId == "u2" }
        val youMember = state.members.find { it.userId == "u1" }

        assertTrue(alexMember?.liveEtaAt?.contains("LATE") == true)
        assertEquals("delayed", alexMember?.journeyStatus)
    }
}
