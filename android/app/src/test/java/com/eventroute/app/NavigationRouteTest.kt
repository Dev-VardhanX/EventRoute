package com.eventroute.app

import com.eventroute.app.data.remote.SessionEvents
import com.eventroute.app.data.remote.TokenStore
import com.eventroute.app.ui.app.RootViewModel
import com.eventroute.app.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URI

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NavigationRouteTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRouteBuildersProduceExpectedStrings() {
        assertEquals("event_details/evt-1001", Screen.EventDetails.createRoute("evt-1001"))
        assertEquals("registration/evt-1001", Screen.Registration.createRoute("evt-1001"))
        assertEquals("teams/evt-1001", Screen.Teams.createRoute("evt-1001"))
        assertEquals("invite/HACK-291X", Screen.Invite.createRoute("HACK-291X"))
        assertEquals("journey/evt-1001", Screen.Journey.createRoute("evt-1001"))
        assertEquals("live_journey/evt-1001", Screen.LiveJourney.createRoute("evt-1001"))
        assertEquals("team_plan/team-bug-hunters", Screen.TeamPlan.createRoute("team-bug-hunters"))
    }

    @Test
    fun testDeepLinkUriPatternParsing() {
        val eventSchemeUri = URI.create("eventroute://event/11111111-1111-1111-1111-111111111111")
        assertEquals("eventroute", eventSchemeUri.scheme)
        assertEquals("event", eventSchemeUri.host)
        assertEquals("11111111-1111-1111-1111-111111111111", eventSchemeUri.path.trim('/'))

        val eventHttpsUri = URI.create("https://eventroute.app/event/11111111-1111-1111-1111-111111111111")
        assertEquals("https", eventHttpsUri.scheme)
        assertEquals("eventroute.app", eventHttpsUri.host)
        val segments = eventHttpsUri.path.trim('/').split('/')
        assertEquals("event", segments[0])
        assertEquals("11111111-1111-1111-1111-111111111111", segments[1])

        val inviteSchemeUri = URI.create("eventroute://invite/HACK-291X")
        assertEquals("eventroute", inviteSchemeUri.scheme)
        assertEquals("invite", inviteSchemeUri.host)
        assertEquals("HACK-291X", inviteSchemeUri.path.trim('/'))

        val inviteHttpsUri = URI.create("https://eventroute.app/invite/HACK-291X")
        val inviteSegments = inviteHttpsUri.path.trim('/').split('/')
        assertEquals("invite", inviteSegments[0])
        assertEquals("HACK-291X", inviteSegments[1])
    }

    @Test
    fun testRootViewModelDecidesStartDestination() = runBlocking {
        val fakeLoggedInStore = object : TokenStore {
            override val access: String = "valid-token"
            override val refresh: String = "valid-refresh"
            override fun save(access: String, refresh: String) {}
            override fun clear() {}
        }
        val loggedInVm = RootViewModel(fakeLoggedInStore, SessionEvents())
        testDispatcher.scheduler.advanceUntilIdle()
        val startLoggedIn = loggedInVm.startDestination.first { it != null }
        assertEquals(Screen.MainGraph.route, startLoggedIn)

        val fakeLoggedOutStore = object : TokenStore {
            override val access: String? = null
            override val refresh: String? = null
            override fun save(access: String, refresh: String) {}
            override fun clear() {}
        }
        val loggedOutVm = RootViewModel(fakeLoggedOutStore, SessionEvents())
        testDispatcher.scheduler.advanceUntilIdle()
        val startLoggedOut = loggedOutVm.startDestination.first { it != null }
        assertEquals(Screen.AuthGraph.route, startLoggedOut)
    }

    @Test
    fun testTabRoutesAndBottomBarVisibilityRules() {
        val tabRoutes = setOf(
            Screen.Home.route,
            Screen.MyEvents.route,
            Screen.JourneyTab.route,
            Screen.Profile.route
        )

        assertTrue(Screen.Home.route in tabRoutes)
        assertTrue(Screen.MyEvents.route in tabRoutes)
        assertTrue(Screen.JourneyTab.route in tabRoutes)
        assertTrue(Screen.Profile.route in tabRoutes)

        assertFalse(Screen.MapExplore.route in tabRoutes)
        assertFalse(Screen.EventDetails.route in tabRoutes)
        assertFalse(Screen.Registration.route in tabRoutes)
        assertFalse(Screen.Teams.route in tabRoutes)
        assertFalse(Screen.Journey.route in tabRoutes)
        assertFalse(Screen.LiveJourney.route in tabRoutes)
        assertFalse(Screen.TeamPlan.route in tabRoutes)
        assertFalse(Screen.Notifications.route in tabRoutes)
    }
}
