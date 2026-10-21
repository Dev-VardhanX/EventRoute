package com.eventroute.app

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MockDataConsistencyTest {

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
    fun testMockJsonDataAndIdConsistency() {
        val eventsJson = json.parseToJsonElement(loadMockJson("events.json")).jsonObject
        val eventItems = eventsJson["items"]?.jsonArray
            ?: throw AssertionError("events.json must have items")
        val validEventIds = eventItems.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet()
        assertTrue("Must have at least one valid event id", validEventIds.isNotEmpty())

        // 1. Check team.json
        val teamJson = json.parseToJsonElement(loadMockJson("team.json")).jsonObject
        val teamId = teamJson["id"]?.jsonPrimitive?.content
        val teamEventId = teamJson["event_id"]?.jsonPrimitive?.content
        assertNotNull("team.json must have id", teamId)
        assertNotNull("team.json must have event_id", teamEventId)
        assertTrue(
            "team.json event_id ($teamEventId) must exist in events.json",
            validEventIds.contains(teamEventId)
        )
        val validTeamIds = mutableSetOf(teamId!!)

        // 2. Check team_plan.json
        val teamPlanJson = json.parseToJsonElement(loadMockJson("team_plan.json")).jsonObject
        val planTeamId = teamPlanJson["team_id"]?.jsonPrimitive?.content
        assertNotNull("team_plan.json must have team_id", planTeamId)
        assertTrue(
            "team_plan.json team_id ($planTeamId) must match an existing team id",
            validTeamIds.contains(planTeamId)
        )

        // 3. Check journey.json
        val journeyJson = json.parseToJsonElement(loadMockJson("journey.json")).jsonObject
        val journeyEventId = journeyJson["event_id"]?.jsonPrimitive?.content
        assertNotNull("journey.json must have event_id", journeyEventId)
        assertTrue(
            "journey.json event_id ($journeyEventId) must exist in events.json",
            validEventIds.contains(journeyEventId)
        )

        // 4. Check registrations.json
        val regJson = json.parseToJsonElement(loadMockJson("registrations.json")).jsonObject
        val regItems = regJson["items"]?.jsonArray
            ?: throw AssertionError("registrations.json must have items")
        assertTrue("registrations.json should have registered items", regItems.isNotEmpty())

        for (item in regItems) {
            val regObj = item.jsonObject
            val regEventId = regObj["event_id"]?.jsonPrimitive?.content
            val regTeamId = regObj["team_id"]?.jsonPrimitive?.contentOrNull

            assertNotNull("registration must have event_id", regEventId)
            assertTrue(
                "registration event_id ($regEventId) must exist in events.json",
                validEventIds.contains(regEventId)
            )

            if (!regTeamId.isNullOrBlank()) {
                assertTrue(
                    "registration team_id ($regTeamId) must exist in validTeamIds",
                    validTeamIds.contains(regTeamId)
                )
            }
        }

        // 5. Check notifications.json
        val notifJson = json.parseToJsonElement(loadMockJson("notifications.json")).jsonObject
        val notifItems = notifJson["items"]?.jsonArray
            ?: throw AssertionError("notifications.json must have items")
        for (item in notifItems) {
            val eventId = item.jsonObject["event_id"]?.jsonPrimitive?.contentOrNull
            if (!eventId.isNullOrBlank()) {
                assertTrue(
                    "notification event_id ($eventId) must exist in events.json",
                    validEventIds.contains(eventId)
                )
            }
        }
    }
}
