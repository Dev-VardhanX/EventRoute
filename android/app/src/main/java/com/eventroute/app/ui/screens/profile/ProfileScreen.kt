package com.eventroute.app.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.R
import com.eventroute.app.domain.model.User
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var firstUnread by remember { mutableStateOf(true) }

    when (val state = uiState) {
        is UiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                LoadingState(message = "Loading profile...")
            }
        }
        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                ErrorState(message = state.message, onRetry = { viewModel.loadProfile() })
            }
        }
        is UiState.Empty -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(title = "Profile not available", message = "Please sign in again.")
            }
        }
        is UiState.Success -> {
            val user = state.data

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(ClayBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 130.dp)
            ) {
                // ── Header + overlapping stats ──
                Box(Modifier.fillMaxWidth().height(330.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(284.dp)
                            .clay(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp), ClayBluePrimary, depth = 14.dp)
                    ) {
                        Box(Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = (-40).dp).size(170.dp).background(Color.White.copy(alpha = 0.22f), CircleShape))
                        Box(Modifier.align(Alignment.CenterStart).offset(x = (-60).dp, y = 30.dp).size(130.dp).background(Color.White.copy(alpha = 0.16f), CircleShape))

                        Column(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box {
                                Box(
                                    Modifier.size(104.dp).clay(RoundedCornerShape(36.dp), Color.White, depth = 12.dp).padding(6.dp).clip(RoundedCornerShape(30.dp))
                                ) {
                                    Image(
                                        painterResource(R.drawable.avatar_john), user.name,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                }
                                ClayIconButton(
                                    Icons.Rounded.CameraAlt, { }, Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp),
                                    contentDescription = "Change photo", size = 38.dp, color = Color.White, tint = ClayBlueDeep
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(user.name, style = ClayType.Title.copy(fontSize = 26.sp))
                            Text(user.collegeName ?: "IIT Delhi • Computer Science", style = ClayType.BodyStrong.copy(fontSize = 14.sp, color = ClayBlueDeep))
                        }
                    }

                    ClayCard(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp),
                        RoundedCornerShape(30.dp), depth = 12.dp
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatItem("12", "EVENTS", Modifier.weight(1f))
                            Box(Modifier.width(1.dp).height(32.dp).background(ClayCardBorder))
                            StatItem("4", "TEAMS", Modifier.weight(1f))
                            Box(Modifier.width(1.dp).height(32.dp).background(ClayCardBorder))
                            StatItem("1.2k", "POINTS", Modifier.weight(1f))
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Text("Home Campus", style = ClayType.Section)
                    Spacer(Modifier.height(12.dp))
                    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), depth = 10.dp) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ClayIconBadge(Icons.Rounded.Home, background = ClayGreenPrimary, tint = ClayGreenText, size = 48.dp, shape = RoundedCornerShape(17.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("DEFAULT START POINT", style = ClayType.Label.copy(fontSize = 9.sp))
                                val startLabel = user.homeLabel ?: user.defaultStart.ifEmpty { "NSUT Dwarka Campus" }
                                Text(startLabel, style = ClayType.BodyStrong.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            }
                            Text(
                                "Edit", style = ClayType.BodyStrong.copy(fontSize = 13.sp, color = ClayBlueDeep),
                                modifier = Modifier.clayPress({ }, true, remember { MutableInteractionSource() })
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("Interests", style = ClayType.Section)
                    Spacer(Modifier.height(12.dp))
                    val inks = listOf(ClayBlueDeep, Color(0xFFEC4899), Color(0xFF22C55E), Color(0xFFD97706))
                    val interests = if (user.interests.isNotEmpty()) user.interests else listOf("Hackathons", "Robotics", "Web3", "Design")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        interests.forEachIndexed { i, raw ->
                            val ink = inks[i % inks.size]
                            val shape = RoundedCornerShape(50)
                            Text(
                                if (raw.startsWith("#")) raw else "#$raw",
                                style = ClayType.BodyStrong.copy(fontSize = 13.sp, color = ink),
                                modifier = Modifier
                                    .clay(shape, Color.White, depth = 6.dp)
                                    .border(2.dp, ink.copy(alpha = 0.32f), shape)
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    ClaySectionHeader("Notifications", action = "Mark as read", onAction = { firstUnread = false })
                    Spacer(Modifier.height(12.dp))
                    NotificationRow(Icons.Rounded.Notifications, ClayBluePrimary, ClayBlueDeep, "Team 'The Bug Hunters' is full!", "2 hours ago", unread = firstUnread)
                    Spacer(Modifier.height(14.dp))
                    NotificationRow(Icons.Rounded.Route, ClayGreenPrimary, ClayGreenText, "Journey to HackX starts tomorrow.", "5 hours ago", unread = false)

                    Spacer(Modifier.height(24.dp))
                    SettingsRow(Icons.Rounded.Settings, "App Settings", ClayTextSecondary, onOpenSettings)
                    Spacer(Modifier.height(14.dp))
                    SettingsRow(Icons.AutoMirrored.Rounded.Logout, "Log Out", ClayAlertPinkText) {
                        viewModel.logout()
                        onLogout()
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(number: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, style = ClayType.Title.copy(fontSize = 24.sp))
        Text(label, style = ClayType.Label.copy(fontSize = 9.sp))
    }
}

@Composable
private fun NotificationRow(icon: ImageVector, bg: Color, ink: Color, text: String, time: String, unread: Boolean) {
    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), backgroundColor = if (unread) ClayBlueLight else Color.White, depth = 9.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ClayIconBadge(icon, background = bg, tint = if (unread) Color.White else ink, size = 42.dp, shape = RoundedCornerShape(15.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text, style = ClayType.BodyStrong.copy(fontSize = 13.sp))
                Text(time, style = ClayType.Caption.copy(fontSize = 10.sp, color = ClayTextMuted))
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, ink: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed = rememberPressed(source)
    Row(
        Modifier
            .fillMaxWidth()
            .clayPress(onClick, true, source)
            .clay(RoundedCornerShape(26.dp), ClaySurfaceSecondary, depth = 8.dp, pressed = pressed)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = ClayType.BodyStrong.copy(fontSize = 14.sp, color = ink), modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = ClayTextMuted, modifier = Modifier.size(22.dp))
    }
}
