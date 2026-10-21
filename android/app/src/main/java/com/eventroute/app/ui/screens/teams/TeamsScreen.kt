package com.eventroute.app.ui.screens.teams

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventroute.app.domain.model.TeamMember
import com.eventroute.app.ui.components.*
import com.eventroute.app.ui.theme.*

@Composable
fun TeamsScreen(
    eventId: String,
    onViewTeamPlan: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inviteCode: String? = null,
    viewModel: TeamsViewModel = viewModel()
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }
    val scanner = remember {
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context)
    }

    LaunchedEffect(eventId, inviteCode) {
        if (!inviteCode.isNullOrBlank()) {
            viewModel.joinTeam(inviteCode) {
                viewModel.loadTeam("team-bug-hunters")
            }
        } else {
            viewModel.loadTeam("team-bug-hunters")
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        ClayTopBar("Team Lobby", onBack)
        Spacer(Modifier.height(18.dp))

        when (val state = uiState) {
            is UiState.Loading -> {
                LoadingState(message = "Loading team lobby...")
            }
            is UiState.Empty -> {
                EmptyState(title = "No team found", message = "Create a team or join using an invite code.")
            }
            is UiState.Error -> {
                ErrorState(message = state.message, onRetry = { viewModel.loadTeam("team-bug-hunters") })
            }
            is UiState.Success -> {
                val team = state.data

                // Blue team header
                ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(34.dp), backgroundColor = ClayBluePrimary, depth = 12.dp) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column {
                                Text("TEAM NAME", style = ClayType.Label.copy(color = ClayBlueDeep))
                                Spacer(Modifier.height(2.dp))
                                Text(team.name, style = ClayType.Title.copy(fontSize = 24.sp))
                                Spacer(Modifier.height(2.dp))
                                Text("HackX 2024 • Delhi-NCR", style = ClayType.Caption.copy(fontSize = 12.sp, color = ClayBlueDeep))
                            }
                            ClayIconBadge(Icons.Rounded.Groups, background = Color.White, tint = ClayBlueDeep, size = 56.dp)
                        }
                        Spacer(Modifier.height(18.dp))
                        ClayWell(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), Color(0xFFD3E8FA)) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("INVITE CODE", style = ClayType.Label.copy(color = ClayBlueDeep))
                                    Text(team.inviteCode, style = ClayType.Mono)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ClayIconButton(
                                        Icons.Rounded.ContentCopy,
                                        { clipboardManager.setText(AnnotatedString(team.inviteCode)) },
                                        contentDescription = "Copy invite code",
                                        size = 40.dp,
                                        tint = ClayBlueDeep
                                    )
                                    ClayIconButton(
                                        Icons.Rounded.QrCode,
                                        { showQrDialog = true },
                                        contentDescription = "Show team QR code",
                                        size = 40.dp,
                                        tint = ClayBlueDeep
                                    )
                                    ClayIconButton(
                                        Icons.Rounded.QrCodeScanner,
                                        {
                                            scanner.startScan()
                                                .addOnSuccessListener { barcode ->
                                                    val raw = barcode.rawValue.orEmpty()
                                                    val code = when {
                                                        raw.contains("/invite/") -> raw.substringAfter("/invite/").trim()
                                                        raw.startsWith("eventroute://invite/") -> raw.substringAfter("eventroute://invite/").trim()
                                                        else -> raw.trim()
                                                    }
                                                    if (code.isNotBlank()) {
                                                        viewModel.joinTeam(code) {
                                                            viewModel.loadTeam(team.id)
                                                        }
                                                    }
                                                }
                                        },
                                        contentDescription = "Scan QR code",
                                        size = 40.dp,
                                        tint = ClayBlueDeep
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Members (${team.members.size}/4)", style = ClayType.Section.copy(fontSize = 16.sp))
                    if (team.members.size < 4) {
                        ClayTag("Waiting for ${4 - team.members.size} more", ClayGreenLight, ClayGreenText)
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(team.members) { MemberClayCard(it) }
                    item { InviteTeammateCard(onClick = { showQrDialog = true }) }
                }

                ClayPillButton(
                    "Share Team Link",
                    {
                        val inviteLink = "https://eventroute.app/invite/${team.inviteCode}"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Join ${team.name} on EventRoute")
                            putExtra(Intent.EXTRA_TEXT, "Join my team '${team.name}' on EventRoute: $inviteLink")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Team Link"))
                    },
                    Modifier.fillMaxWidth(),
                    backgroundColor = ClayPinkPrimary,
                    textColor = ClayPinkText,
                    icon = Icons.Rounded.IosShare
                )
                Spacer(Modifier.height(14.dp))
                ClayPillButton("Confirm Team & Proceed", {
                    viewModel.confirmTeam(team.id)
                    onViewTeamPlan(team.id)
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    "ONLY THE LEADER CAN CONFIRM THE TEAM",
                    style = ClayType.Label.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showQrDialog) {
                    Dialog(onDismissRequest = { showQrDialog = false }) {
                        ClayCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            backgroundColor = Color.White,
                            depth = 14.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Team Invite QR", style = ClayType.Title.copy(fontSize = 22.sp))
                                Spacer(Modifier.height(4.dp))
                                Text("Scan to join ${team.name}", style = ClayType.Caption)
                                Spacer(Modifier.height(18.dp))
                                ClayQrCode(
                                    content = "https://eventroute.app/invite/${team.inviteCode}",
                                    size = 200.dp
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(team.inviteCode, style = ClayType.Mono.copy(fontSize = 20.sp, letterSpacing = 2.sp))
                                Spacer(Modifier.height(18.dp))
                                ClaySmallButton(
                                    "Close",
                                    { showQrDialog = false },
                                    Modifier.fillMaxWidth(),
                                    backgroundColor = ClayBluePrimary,
                                    textColor = ClayBlueText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberClayCard(member: TeamMember) {
    val ready = member.journeyStatus.equals("READY", ignoreCase = true) ||
            member.journeyStatus.equals("idle", ignoreCase = true)
    ClayCard(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), depth = 9.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayAvatar(
                member.name, size = 46.dp,
                status = if (ready) ClayGreenBright else Color(0xFFFB923C)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (member.isLeader) "${member.name.removeSuffix(" (Leader)")} (Leader)" else member.name,
                    style = ClayType.BodyStrong.copy(fontSize = 15.sp)
                )
                Text(member.collegeName.ifEmpty { "NSUT Dwarka" }, style = ClayType.Caption)
            }
            ClayTag(
                if (ready) "READY" else member.journeyStatus.uppercase(),
                if (ready) ClayAmberLight else ClayGrayChip,
                if (ready) ClayAmberText else ClayTextSecondary
            )
        }
    }
}

@Composable
private fun InviteTeammateCard(onClick: () -> Unit = {}) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayPress(onClick, true, source)
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFFCBD5E1),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                    )
                )
            }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(30.dp).clay(CircleShape, Color(0xFFCBD5E1), depth = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Invite Teammate", style = ClayType.BodyStrong.copy(fontSize = 13.sp, color = ClayTextMuted))
    }
}
