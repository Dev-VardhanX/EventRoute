package com.eventroute.app

import com.eventroute.app.data.remote.SessionEvents
import com.eventroute.app.data.remote.TokenAuthenticator
import com.eventroute.app.data.remote.TokenStore
import com.eventroute.app.data.remote.api.AuthRefreshApi
import com.eventroute.app.data.remote.dto.RefreshRequest
import com.eventroute.app.data.remote.dto.TokenResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class InMemoryTokenStore(
    override var access: String? = "initial_access",
    override var refresh: String? = "initial_refresh"
) : TokenStore {
    var clearCalls = 0
    override fun save(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }
    override fun clear() {
        access = null
        refresh = null
        clearCalls++
    }
}

class FakeRefreshApi : AuthRefreshApi {
    val refreshCalls = AtomicInteger(0)
    var shouldFail = false
    override suspend fun refresh(request: RefreshRequest): TokenResponse {
        refreshCalls.incrementAndGet()
        if (shouldFail) throw RuntimeException("Refresh rejected")
        return TokenResponse(
            accessToken = "refreshed_access",
            refreshToken = "refreshed_refresh"
        )
    }
}

class TokenAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var sessionEvents: SessionEvents
    private lateinit var fakeRefreshApi: FakeRefreshApi
    private lateinit var authenticator: TokenAuthenticator

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        tokenStore = InMemoryTokenStore()
        sessionEvents = SessionEvents()
        fakeRefreshApi = FakeRefreshApi()
        authenticator = TokenAuthenticator(
            tokens = tokenStore,
            refreshApi = { fakeRefreshApi },
            session = sessionEvents
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun create401Response(requestToken: String = "initial_access", priorResponse: Response? = null): Response {
        val req = Request.Builder()
            .url(server.url("/api/v1/test"))
            .header("Authorization", "Bearer $requestToken")
            .build()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(priorResponse)
            .build()
    }

    @Test
    fun test401TriggersExactlyOneRefreshAndOneRetry() {
        val response = create401Response()
        val retryRequest = authenticator.authenticate(null, response)

        assertNotNull(retryRequest)
        assertEquals("Bearer refreshed_access", retryRequest?.header("Authorization"))
        assertEquals(1, fakeRefreshApi.refreshCalls.get())
        assertEquals("refreshed_access", tokenStore.access)
        assertEquals("refreshed_refresh", tokenStore.refresh)
    }

    @Test
    fun testSecond401ReturnsNullAndDoesNotRetryAgain() {
        val firstResponse = create401Response()
        val secondResponse = create401Response(
            requestToken = "refreshed_access",
            priorResponse = firstResponse
        )

        val retryRequest = authenticator.authenticate(null, secondResponse)
        assertNull(retryRequest)
    }

    @Test
    fun testFailedRefreshClearsTokensAndLogsOut() = runBlocking {
        fakeRefreshApi.shouldFail = true
        var loggedOutEmitted = false
        val job = launch {
            sessionEvents.loggedOut.first()
            loggedOutEmitted = true
        }

        val response = create401Response()
        val retryRequest = authenticator.authenticate(null, response)

        assertNull(retryRequest)
        assertEquals(0, tokenStore.access?.length ?: 0)
        assertNull(tokenStore.refresh)
        assertTrue(tokenStore.clearCalls > 0)

        withTimeoutOrNull(1000) { job.join() }
        assertTrue(loggedOutEmitted)
    }

    @Test
    fun testConcurrent401sCauseOnlyOneRefresh() {
        val latch = CountDownLatch(2)
        val responses = listOf(create401Response(), create401Response())
        val retryRequests = arrayOfNulls<Request>(2)

        val thread1 = Thread {
            retryRequests[0] = authenticator.authenticate(null, responses[0])
            latch.countDown()
        }
        val thread2 = Thread {
            retryRequests[1] = authenticator.authenticate(null, responses[1])
            latch.countDown()
        }

        thread1.start()
        thread2.start()
        latch.await(3, TimeUnit.SECONDS)

        assertEquals("Exactly one refresh should be called for concurrent 401s", 1, fakeRefreshApi.refreshCalls.get())
        assertNotNull(retryRequests[0])
        assertNotNull(retryRequests[1])
        assertEquals("Bearer refreshed_access", retryRequests[0]?.header("Authorization"))
        assertEquals("Bearer refreshed_access", retryRequests[1]?.header("Authorization"))
    }
}
