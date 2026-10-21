package com.eventroute.app.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.di.AppModule
import com.eventroute.app.domain.live.LiveFeedSimulator
import com.eventroute.app.notifications.NotificationRouter
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DebugScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedError by remember { mutableStateOf(DebugFlags.simulatedError) }
    var latencyMs by remember { mutableStateOf(DebugFlags.latencyMs.toFloat()) }
    var isOffline by remember { mutableStateOf(DebugFlags.isOffline) }

    var errorDropdownOpen by remember { mutableStateOf(false) }

    val notificationTypes = listOf(
        "leave_soon", "leave_now", "event_reminder", "deadline_reminder",
        "teammate_delayed", "team_confirmed", "registration_confirmed", "announcement"
    )
    var selectedNotifType by remember { mutableStateOf("leave_soon") }
    var notifDropdownOpen by remember { mutableStateOf(false) }

    var fcmToken by remember { mutableStateOf("Fetching token...") }

    LaunchedEffect(Unit) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> fcmToken = token }
                .addOnFailureListener { err -> fcmToken = "Unavailable (${err.message})" }
        } catch (e: Exception) {
            fcmToken = "Firebase not initialized"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back", size = 46.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, tint = ClayAlertPink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Developer Tools", style = ClayType.Title.copy(fontSize = 20.sp))
            }
            Spacer(Modifier.width(46.dp))
        }

        Spacer(Modifier.height(20.dp))

        // SECTION 1: Error & Network Simulator
        Text("FAILURE & NETWORK SIMULATOR", style = ClayType.Label.copy(color = ClayBlueDeep))
        Spacer(Modifier.height(10.dp))
        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), depth = 8.dp) {
            Column(Modifier.padding(18.dp)) {
                Text("Simulate API Error on Next Call", style = ClayType.BodyStrong.copy(fontSize = 13.sp))
                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clay(RoundedCornerShape(16.dp), Color(0xFFF1F5F9), depth = 4.dp)
                            .clickable { errorDropdownOpen = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedError?.code ?: "None (Clean Call)",
                            style = ClayType.Body.copy(color = if (selectedError != null) ClayAlertPink else ClayBlueDeep)
                        )
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = ClayBlueDeep)
                    }

                    DropdownMenu(
                        expanded = errorDropdownOpen,
                        onDismissRequest = { errorDropdownOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Clear Error)") },
                            onClick = {
                                selectedError = null
                                DebugFlags.simulatedError = null
                                errorDropdownOpen = false
                            }
                        )
                        SimulatedError.values().forEach { err ->
                            DropdownMenuItem(
                                text = { Text("${err.code}: ${err.description}") },
                                onClick = {
                                    selectedError = err
                                    DebugFlags.simulatedError = err
                                    errorDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Offline Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Simulate Offline Mode", style = ClayType.BodyStrong.copy(fontSize = 13.sp))
                        Text("Drops all network/mock requests", style = ClayType.Caption.copy(color = ClayTextMuted))
                    }
                    Switch(
                        checked = isOffline,
                        onCheckedChange = {
                            isOffline = it
                            DebugFlags.isOffline = it
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Latency Slider
                Text("Network Latency: ${latencyMs.toInt()} ms", style = ClayType.BodyStrong.copy(fontSize = 13.sp))
                Slider(
                    value = latencyMs,
                    onValueChange = {
                        latencyMs = it
                        DebugFlags.latencyMs = it.toLong()
                    },
                    valueRange = 0f..5000f,
                    steps = 10
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // SECTION 2: Notification & Push Testing
        Text("NOTIFICATION & PUSH TESTING", style = ClayType.Label.copy(color = ClayBlueDeep))
        Spacer(Modifier.height(10.dp))
        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), depth = 8.dp) {
            Column(Modifier.padding(18.dp)) {
                Text("Device FCM Token", style = ClayType.BodyStrong.copy(fontSize = 13.sp))
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clay(RoundedCornerShape(16.dp), Color(0xFFF1F5F9), depth = 4.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fcmToken,
                        style = ClayType.Mono.copy(fontSize = 11.sp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", fcmToken))
                            Toast.makeText(context, "FCM Token copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, "Copy Token", tint = ClayBlueDeep)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Fire Notification in 10 Seconds", style = ClayType.BodyStrong.copy(fontSize = 13.sp))
                Spacer(Modifier.height(6.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clay(RoundedCornerShape(16.dp), Color(0xFFF1F5F9), depth = 4.dp)
                            .clickable { notifDropdownOpen = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedNotifType, style = ClayType.Body.copy(color = ClayBlueDeep))
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = ClayBlueDeep)
                    }

                    DropdownMenu(
                        expanded = notifDropdownOpen,
                        onDismissRequest = { notifDropdownOpen = false }
                    ) {
                        notificationTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    selectedNotifType = type
                                    notifDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                ClaySmallButton(
                    text = "Trigger '$selectedNotifType' in 10s",
                    onClick = {
                        val type = selectedNotifType
                        coroutineScope.launch {
                            Toast.makeText(context, "Scheduled: '$type' will fire in 10s", Toast.LENGTH_SHORT).show()
                            delay(10_000L)
                            NotificationRouter.showNotification(
                                context = context,
                                type = type,
                                eventId = "11111111-1111-1111-1111-111111111111",
                                teamId = "team-bug-hunters"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = ClayBluePrimary,
                    textColor = ClayBlueText
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // SECTION 3: Live Simulator Controls
        Text("LIVE JOURNEY SIMULATOR", style = ClayType.Label.copy(color = ClayBlueDeep))
        Spacer(Modifier.height(10.dp))
        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), depth = 8.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ClaySmallButton(
                    text = "Jump to LATE Scenario (Traffic Alert + Alex Delayed)",
                    onClick = {
                        LiveFeedSimulator.jumpToLateScenario()
                        Toast.makeText(context, "Live feed jumped to LATE scenario", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = ClayAlertPink,
                    textColor = ClayPinkText
                )

                ClaySmallButton(
                    text = "Restart Simulation",
                    onClick = {
                        LiveFeedSimulator.restartSimulation()
                        Toast.makeText(context, "Live feed simulation reset to start", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFF1F5F9),
                    textColor = ClayBlueDeep
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // SECTION 4: Storage & State Reset
        Text("LOCAL STORAGE & RESET", style = ClayType.Label.copy(color = ClayBlueDeep))
        Spacer(Modifier.height(10.dp))
        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), depth = 8.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ClaySmallButton(
                    text = "Clear Local Data & Registrations",
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                AppModule.database.clear()
                            }
                            Toast.makeText(context, "Local database tables cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFF1F5F9),
                    textColor = Color(0xFFDC2626)
                )

                ClaySmallButton(
                    text = "Reset All Notification Settings",
                    onClick = {
                        coroutineScope.launch {
                            AppModule.notificationSettingsRepository.resetToDefaults()
                            Toast.makeText(context, "Notification settings reset to default", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFF1F5F9),
                    textColor = ClayBlueDeep
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
