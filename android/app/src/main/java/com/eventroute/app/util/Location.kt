package com.eventroute.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.eventroute.app.domain.model.GeoPoint
import com.eventroute.app.domain.repository.ProfileRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }

class LocationProvider(
    private val context: Context,
    private val profileRepository: ProfileRepository,
) {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    suspend fun getCurrentLocation(): GeoPoint {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            val loc = withTimeoutOrNull(5000L) {
                try {
                    val cts = CancellationTokenSource()
                    val result: Location? = fusedClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).awaitResult()
                    if (result != null) {
                        GeoPoint(result.latitude, result.longitude)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            if (loc != null) return loc
        }

        // Fall back to profile saved start point
        val profile = profileRepository.getProfile().getOrNull()
        if (profile?.homeLat != null && profile.homeLng != null) {
            return GeoPoint(profile.homeLat, profile.homeLng)
        }

        // Default Delhi location
        return GeoPoint(28.6139, 77.2090)
    }
}

class LocationPermissionState(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit
)

@Composable
fun rememberLocationPermissionState(
    onPermissionGranted: (() -> Unit)? = null
): LocationPermissionState {
    val context = LocalContext.current
    fun checkPerm() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(checkPerm()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) {
            onPermissionGranted?.invoke()
        }
    }

    return remember(hasPermission) {
        LocationPermissionState(
            hasPermission = hasPermission,
            requestPermission = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )
    }
}
