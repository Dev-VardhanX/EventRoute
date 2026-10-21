package com.eventroute.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val time: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
)

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        ClayTopBar("Notifications & Alerts", onBack)

        when (val state = uiState) {
            is UiState.Loading -> {
                LoadingState(message = "Checking for alerts...")
            }
            is UiState.Empty -> {
                EmptyState(title = "No new notifications", message = "You're all caught up with your events and journey alerts.", icon = Icons.Rounded.Notifications)
            }
            is UiState.Error -> {
                ErrorState(message = state.message, onRetry = { viewModel.loadNotifications() })
            }
            is UiState.Success -> {
                val notifications = state.data.map { n ->
                    val (icon, bg, tint) = when (n.type.lowercase()) {
                        "delay", "warning", "traffic" -> Triple(Icons.Rounded.Warning, ClayAlertPink, ClayPinkText)
                        "team", "handshake" -> Triple(Icons.Rounded.Handshake, ClayGreenPrimary, ClayGreenText)
                        else -> Triple(Icons.Rounded.Alarm, ClayAmberLight, ClayAmberText)
                    }
                    NotificationItem(
                        id = n.id,
                        title = n.title,
                        body = n.body,
                        time = n.timeAgo.ifEmpty { "Just now" },
                        icon = icon,
                        iconBg = bg,
                        iconTint = tint
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notifications) { n ->
                        ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), depth = 10.dp) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                                ClayIconBadge(n.icon, background = n.iconBg, tint = n.iconTint, size = 44.dp, shape = RoundedCornerShape(16.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(n.title, style = ClayType.BodyStrong.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                                        Text(n.time, style = ClayType.Caption.copy(fontSize = 10.sp, color = ClayTextMuted))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(n.body, style = ClayType.Body.copy(fontSize = 12.sp, lineHeight = 17.sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
