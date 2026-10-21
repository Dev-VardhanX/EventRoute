package com.eventroute.app.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.eventroute.app.MainActivity
import com.eventroute.app.R
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.notifications.NotifChannel
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.eventroute.app.action.START_LOCATION_SERVICE"
        const val ACTION_STOP = "com.eventroute.app.action.STOP_LOCATION_SERVICE"
        const val EXTRA_TEAM_ID = "extra_team_id"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val NOTIFICATION_ID = 4001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context, teamId: String = "team-bug-hunters", eventId: String = "11111111-1111-1111-1111-111111111111") {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TEAM_ID, teamId)
                putExtra(EXTRA_EVENT_ID, eventId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var teamId: String = "team-bug-hunters"
    private var eventId: String = "11111111-1111-1111-1111-111111111111"

    private val venueLocation = GeoPoint(28.5459, 77.1926) // IIT Delhi

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val geoPoint = GeoPoint(loc.latitude, loc.longitude)

                serviceScope.launch {
                    AppModule.liveLocationRepository.publishLocation(teamId, geoPoint)

                    // Check if within 150m of venue
                    val distResults = FloatArray(1)
                    Location.distanceBetween(
                        loc.latitude, loc.longitude,
                        venueLocation.lat, venueLocation.lng,
                        distResults
                    )
                    if (distResults[0] <= 150f) {
                        AppModule.liveTeamRepository.updateUserStatus(teamId, "arrived")
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        teamId = intent?.getStringExtra(EXTRA_TEAM_ID) ?: "team-bug-hunters"
        eventId = intent?.getStringExtra(EXTRA_EVENT_ID) ?: "11111111-1111-1111-1111-111111111111"

        val notification = buildPersistentNotification()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
        _isRunning.value = true

        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine) {
            // Permission missing, rely on simulator fallback
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Handled safely
        }
    }

    private fun buildPersistentNotification(): Notification {
        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1001,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("eventroute://journey/$eventId"), this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1002,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotifChannel.JOURNEY.id)
            .setSmallIcon(R.drawable.ic_stat_eventroute)
            .setContentTitle("EventRoute Live Journey")
            .setContentText("Sharing live location with your team")
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop sharing", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        _isRunning.value = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
