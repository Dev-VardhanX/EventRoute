package com.eventroute.app

import com.eventroute.app.data.remote.ApiException
import com.eventroute.app.data.remote.ErrorInterceptor
import com.eventroute.app.domain.usecase.friendly
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ErrorParsingTest {

    @Test
    fun testEventFullErrorParsing() {
        val jsonBody = """{"code":"EVENT_FULL","message":"This event is full"}"""
        val client = OkHttpClient.Builder()
            .addInterceptor(ErrorInterceptor())
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body(jsonBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        try {
            client.newCall(Request.Builder().url("https://eventroute.app/api/v1/test").build()).execute()
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals("EVENT_FULL", e.code)
            assertEquals("This event is full.", e.friendly())
            assertEquals(400, e.httpStatus)
        }
    }

    @Test
    fun testIOExceptionBecomesNetworkApiException() {
        val client = OkHttpClient.Builder()
            .addInterceptor(ErrorInterceptor())
            .addInterceptor {
                throw IOException("Connection reset by peer")
            }
            .build()

        try {
            client.newCall(Request.Builder().url("https://eventroute.app/api/v1/test").build()).execute()
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals("NETWORK", e.code)
            assertEquals("No connection. Check your internet and retry.", e.friendly())
            assertEquals(0, e.httpStatus)
        }
    }

    @Test
    fun testAllContractFriendlyCodes() {
        val testCases = mapOf(
            "EVENT_FULL" to "This event is full.",
            "EVENT_CLOSED" to "Registration for this event is closed.",
            "ALREADY_REGISTERED" to "You are already registered for this event.",
            "TEAM_FULL" to "This team is already full.",
            "TEAM_TOO_SMALL" to "Your team needs more members before it can be confirmed.",
            "NOT_ELIGIBLE" to "You are not eligible for this event.",
            "TIME_CLASH" to "This overlaps with another event you registered for.",
            "INVITE_INVALID" to "That invite code is not valid.",
            "ROUTE_UNAVAILABLE" to "We couldn't find a route right now. Try again.",
            "RATE_LIMITED" to "Too many requests. Please wait a moment.",
            "UNAUTHENTICATED" to "Please log in again.",
            "NETWORK" to "No connection. Check your internet and retry."
        )

        for ((code, expectedFriendly) in testCases) {
            val ex = ApiException(code, "Backend error message", 400)
            assertEquals(expectedFriendly, ex.friendly())
        }
    }
}
