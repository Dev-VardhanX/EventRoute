package com.eventroute.app

import com.eventroute.app.data.mapper.toDomain
import com.eventroute.app.data.remote.ApiMode
import com.eventroute.app.data.remote.dto.EventListResponse
import com.eventroute.app.data.remote.dto.JourneyPlanResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MapperAndRepositorySwitchTest {

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

    @Test
    fun testEventsJsonMapping() {
        val jsonString = loadMockJson("events.json")
        val response = json.decodeFromString<EventListResponse>(jsonString)

        assertTrue("events.json should contain items", response.items.isNotEmpty())
        assertEquals(6, response.items.size)

        val firstEventDto = response.items.first()
        val domainEvent = firstEventDto.toDomain()

        assertEquals("HackX 2024", domainEvent.title)
        assertEquals("IIT Delhi", domainEvent.collegeName)
        assertEquals(28.5459, domainEvent.venueLat, 0.001)
        assertEquals(77.1926, domainEvent.venueLng, 0.001)

        // Timezone conversion to Asia/Kolkata
        // 2026-10-24T04:30:00Z -> 10:00 AM IST
        assertTrue("Formatted date should not be empty", domainEvent.formattedDate.isNotEmpty())
        assertTrue("Formatted time should contain 10:00 AM", domainEvent.formattedTime.contains("10:00 AM"))
    }

    @Test
    fun testJourneyJsonMappingAndLeaveByString() {
        val jsonString = loadMockJson("journey.json")
        val response = json.decodeFromString<JourneyPlanResponse>(jsonString)
        val journey = response.toDomain(timezone = "Asia/Kolkata")

        assertEquals("plan-0001", journey.planId)
        assertEquals("NSUT Campus, Dwarka", journey.originName)
        assertEquals("IIT Delhi, Hauz Khas", journey.destinationName)

        // 2026-10-24T02:45:00Z -> 08:15 AM
        assertEquals("08:15 AM", journey.leaveBy)
        // 2026-10-24T03:30:00Z -> 09:00 AM
        assertEquals("09:00 AM", journey.arriveAt)

        assertEquals(45, journey.bufferMinutes)
        assertEquals(4, journey.bufferBreakdown.size)
        assertEquals("Base buffer", journey.bufferBreakdown[0].label)
        assertEquals(20, journey.bufferBreakdown[0].minutes)

        // Verify transfer step is inserted between Blue and Pink lines
        assertTrue("Should contain steps", journey.steps.isNotEmpty())
        val transferStep = journey.steps.find { it.mode == "transfer" }
        assertNotNull("Should contain a transfer step between consecutive metro legs", transferStep)
        assertTrue(transferStep!!.instruction.contains("Pink Line"))
    }

    @Test
    fun testRepositorySwitchApiMode() {
        // By default in debug with empty LIVE_ENDPOINTS
        assertTrue("isMock('events') should be true by default in debug", ApiMode.isMock("events"))
        assertTrue("isMock('auth') should be true by default in debug", ApiMode.isMock("auth"))

        // When 'events' is in LIVE_ENDPOINTS
        val isEventsMock = ApiMode.isMock("events", useMock = true, liveEndpoints = "events,teams")
        assertFalse("isMock('events') should be false when 'events' is listed in LIVE_ENDPOINTS", isEventsMock)

        val isTeamsMock = ApiMode.isMock("teams", useMock = true, liveEndpoints = "events,teams")
        assertFalse("isMock('teams') should be false when 'teams' is listed in LIVE_ENDPOINTS", isTeamsMock)

        val isAuthMock = ApiMode.isMock("auth", useMock = true, liveEndpoints = "events,teams")
        assertTrue("isMock('auth') should be true when 'auth' is NOT listed in LIVE_ENDPOINTS", isAuthMock)

        // When USE_MOCK is false (release build)
        val isEventsMockRelease = ApiMode.isMock("events", useMock = false, liveEndpoints = "")
        assertFalse("isMock should be false in release mode", isEventsMockRelease)
    }
}
