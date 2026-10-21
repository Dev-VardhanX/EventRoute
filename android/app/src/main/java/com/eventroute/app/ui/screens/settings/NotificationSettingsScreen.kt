package com.eventroute.app.ui.screens.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*
import java.time.LocalTime

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ClayTopBar("Notification Settings", onBack)
        Spacer(Modifier.height(18.dp))

        Text("Notification Channels", style = ClayType.Section, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(12.dp))

        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), depth = 10.dp) {
            Column(Modifier.padding(18.dp)) {
                SettingToggleRow(
                    title = "Leave & Event Reminders",
                    description = "Smart departure alerts 15m prior, leave-now calls, and 24h event reminders.",
                    checked = settings.remindersEnabled,
                    onCheckedChange = { viewModel.toggleReminders(it) }
                )
                Spacer(Modifier.height(16.dp))
                SettingToggleRow(
                    title = "Team Updates",
                    description = "Teammates joining/leaving, squad lock confirmations, and meetup suggestions.",
                    checked = settings.teamEnabled,
                    onCheckedChange = { viewModel.toggleTeam(it) }
                )
                Spacer(Modifier.height(16.dp))
                SettingToggleRow(
                    title = "Transit Alerts & Changes",
                    description = "Metro delays, arrival risk flags, and event time updates.",
                    checked = settings.alertsEnabled,
                    onCheckedChange = { viewModel.toggleAlerts(it) }
                )
                Spacer(Modifier.height(16.dp))
                SettingToggleRow(
                    title = "Announcements",
                    description = "Organizer broadcasts and Delhi-NCR circuit updates.",
                    checked = settings.announcementsEnabled,
                    onCheckedChange = { viewModel.toggleAnnouncements(it) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Quiet Hours", style = ClayType.Section, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(12.dp))

        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), depth = 10.dp) {
            Column(Modifier.padding(18.dp)) {
                SettingToggleRow(
                    title = "Enable Quiet Hours",
                    description = "Mutes non-critical alerts during this window. Leave Now and Event Cancelled will always notify.",
                    checked = settings.quietHoursEnabled,
                    onCheckedChange = { viewModel.toggleQuietHours(it) }
                )

                if (settings.quietHoursEnabled) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ClayTimeChip(
                            label = "Start Time",
                            time = settings.quietHoursStart,
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> viewModel.setQuietStart(LocalTime.of(hour, minute)) },
                                    settings.quietHoursStart.hour,
                                    settings.quietHoursStart.minute,
                                    false
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ClayTimeChip(
                            label = "End Time",
                            time = settings.quietHoursEnd,
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> viewModel.setQuietEnd(LocalTime.of(hour, minute)) },
                                    settings.quietHoursEnd.hour,
                                    settings.quietHoursEnd.minute,
                                    false
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Privacy & Location", style = ClayType.Section, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(12.dp))

        ClayCard(
            Modifier.fillMaxWidth(),
            RoundedCornerShape(28.dp),
            backgroundColor = ClayBlueLight,
            depth = 8.dp
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                ClayIconBadge(
                    Icons.Rounded.Visibility,
                    background = Color.White,
                    tint = ClayBlueDeep,
                    size = 46.dp,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Who can see my location?",
                        style = ClayType.BodyStrong.copy(fontSize = 15.sp, color = ClayBlueDeep)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your live location is only visible to your confirmed teammates while a Live Journey is active. It is never public and automatically turns off when you arrive or stop sharing.",
                        style = ClayType.Caption.copy(fontSize = 12.sp, color = ClayTextSecondary)
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = ClayType.BodyStrong.copy(fontSize = 15.sp))
            Spacer(Modifier.height(2.dp))
            Text(description, style = ClayType.Caption.copy(fontSize = 11.sp, color = ClayTextMuted))
        }
        ClaySwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
