package com.eventroute.app.domain.live

import android.content.Context
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.domain.model.TeamLiveState
import com.eventroute.app.domain.model.TeamMember
import com.eventroute.app.domain.util.PolylineCodec
import com.eventroute.app.notifications.NotificationRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

object LiveFeedSimulator {

    const val TICK_MS: Long = 3000L

    val DEFAULT_POLYLINES = listOf(
        "wwrmD_geuMgOjMwQjCgYjC",
        "_utmD{oduMkMkiAkMgiB_NgiBoi@_|Bgw@_jA_l@ogA",
        "opzmDg_vuMfY_`AfpAgiBfiBw|Af{CgpA~{B_q@noBg^",
        "_qgmDwjcvMvQgEjRwB"
    )

    val DEFAULT_ROUTE: List<GeoPoint> by lazy {
        DEFAULT_POLYLINES.flatMap { PolylineCodec.decode(it) }
    }

    private val venueLocation = GeoPoint(28.5459, 77.1926) // IIT Delhi
    private var appContext: Context? = null

    private var currentRoute: List<GeoPoint> = DEFAULT_ROUTE

    // Simulation states
    private var tickCount = 0
    private var youFraction = 0.55
    private var sarahFraction = 0.45
    private var alexFraction = 0.30
    private val normalSpeed = 1.0
    private val alexSpeed = 0.6
    private var alertInjected = false
    private var alertMessage: String? = null
    private var youStatus = "traveling"
    private var customUserLocation: GeoPoint? = null

    private val _liveState = MutableStateFlow(buildState())
    val liveState: StateFlow<TeamLiveState> = _liveState.asStateFlow()

    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setRoute(route: List<GeoPoint>) {
        if (route.isNotEmpty()) {
            currentRoute = route
            _liveState.value = buildState()
        }
    }

    fun haversineM(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371000.0 // meters
        val lat1Rad = Math.toRadians(a.lat)
        val lat2Rad = Math.toRadians(b.lat)
        val deltaLat = Math.toRadians(b.lat - a.lat)
        val deltaLng = Math.toRadians(b.lng - a.lng)
        val sinDeltaLat = sin(deltaLat / 2)
        val sinDeltaLng = sin(deltaLng / 2)
        val h = sinDeltaLat * sinDeltaLat +
                cos(lat1Rad) * cos(lat2Rad) * sinDeltaLng * sinDeltaLng
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return r * c
    }

    fun pointAt(route: List<GeoPoint>, fraction: Double): GeoPoint {
        if (route.isEmpty()) return GeoPoint(0.0, 0.0)
        if (route.size == 1) return route.first()
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0.0) return route.first()
        if (f >= 1.0) return route.last()
        val seg = route.zipWithNext { a, b -> haversineM(a, b) }
        val total = seg.sum()
        if (total == 0.0) return route.first()
        val target = total * f
        var acc = 0.0
        for (i in seg.indices) {
            if (acc + seg[i] >= target) {
                val segLen = seg[i]
                val t = if (segLen > 0.0) (target - acc) / segLen else 0.0
                return GeoPoint(
                    route[i].lat + (route[i + 1].lat - route[i].lat) * t,
                    route[i].lng + (route[i + 1].lng - route[i].lng) * t
                )
            }
            acc += seg[i]
        }
        return route.last()
    }

    @Synchronized
    fun startSimulation() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    @Synchronized
    fun stopSimulation() {
        tickerJob?.cancel()
        tickerJob = null
    }

    @Synchronized
    fun tick() {
        tickCount++

        // Step sizes per tick
        val step = 0.012
        if (youStatus != "arrived") {
            youFraction = (youFraction + step * normalSpeed).coerceAtMost(1.0)
        }
        sarahFraction = (sarahFraction + step * normalSpeed).coerceAtMost(1.0)
        alexFraction = (alexFraction + step * alexSpeed).coerceAtMost(1.0)

        // Traffic alert injected after about 20s (7 ticks * 3s = 21s)
        if (!alertInjected && tickCount >= 7) {
            injectTrafficAlert("Pink Line delayed 5 mins")
        }

        // Auto-reached check for 'You' within 150m
        val currentYouPos = customUserLocation ?: pointAt(currentRoute, youFraction)
        if (haversineM(currentYouPos, venueLocation) <= 150.0) {
            youStatus = "arrived"
            youFraction = 1.0
        }

        _liveState.value = buildState()
    }

    fun updateUserLocation(location: GeoPoint) {
        customUserLocation = location
        if (haversineM(location, venueLocation) <= 150.0) {
            youStatus = "arrived"
            youFraction = 1.0
        }
        _liveState.value = buildState()
    }

    fun updateUserStatus(status: String) {
        youStatus = status
        if (status.equals("arrived", ignoreCase = true) || status.equals("reached", ignoreCase = true)) {
            youFraction = 1.0
        }
        _liveState.value = buildState()
    }

    fun injectTrafficAlert(message: String = "Pink Line delayed 5 mins") {
        alertInjected = true
        alertMessage = message
        _liveState.value = buildState()

        appContext?.let { ctx ->
            NotificationRouter.showNotification(
                context = ctx,
                type = "teammate_delayed",
                teamId = "team-bug-hunters",
                title = "Teammate Delayed",
                body = "Alex is running late: $message"
            )
        }
    }

    fun jumpToLateScenario() {
        tickCount = 8
        youFraction = 0.65
        sarahFraction = 0.58
        alexFraction = 0.32
        injectTrafficAlert("Pink Line delayed 5 mins")
    }

    fun restartSimulation() {
        tickCount = 0
        youFraction = 0.55
        sarahFraction = 0.45
        alexFraction = 0.30
        alertInjected = false
        alertMessage = null
        youStatus = "traveling"
        customUserLocation = null
        _liveState.value = buildState()
    }

    fun getAlexFraction(): Double = alexFraction
    fun getSarahFraction(): Double = sarahFraction
    fun getYouFraction(): Double = youFraction

    private fun buildState(): TeamLiveState {
        val youPos = customUserLocation ?: pointAt(currentRoute, youFraction)
        val sarahPos = pointAt(currentRoute, sarahFraction)
        val alexPos = pointAt(currentRoute, alexFraction)

        val youEta = if (youStatus.equals("arrived", ignoreCase = true) || youStatus.equals("reached", ignoreCase = true)) {
            "Reached"
        } else {
            val mins = maxOf(1, ((1.0 - youFraction) * 26).toInt())
            "$mins min"
        }

        val sarahBonus = if (alertInjected) 5 else 0
        val sarahMins = maxOf(1, ((1.0 - sarahFraction) * 28).toInt() + sarahBonus)
        val sarahEta = "$sarahMins min"

        val alexBonus = if (alertInjected) 5 else 0
        val alexMins = maxOf(1, ((1.0 - alexFraction) * 38).toInt() + alexBonus)
        val alexEta = "$alexMins min (LATE)"

        val members = listOf(
            TeamMember(
                userId = "u1",
                name = "John Doe (You)",
                collegeName = "NSUT",
                isLeader = true,
                liveLat = youPos.lat,
                liveLng = youPos.lng,
                liveEtaAt = youEta,
                journeyStatus = youStatus
            ),
            TeamMember(
                userId = "u2",
                name = "Sarah Smith",
                collegeName = "NSUT",
                isLeader = false,
                liveLat = sarahPos.lat,
                liveLng = sarahPos.lng,
                liveEtaAt = sarahEta,
                journeyStatus = "traveling"
            ),
            TeamMember(
                userId = "u3",
                name = "Alex Johnson",
                collegeName = "NSUT",
                isLeader = false,
                liveLat = alexPos.lat,
                liveLng = alexPos.lng,
                liveEtaAt = alexEta,
                journeyStatus = "delayed"
            )
        )

        return TeamLiveState(
            teamId = "team-bug-hunters",
            members = members,
            alertMessage = alertMessage,
            targetArrivalAt = "09:30 AM",
            venueLocation = venueLocation,
            venueName = "IIT Delhi"
        )
    }
}
