package com.eventroute.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.domain.model.Event
import com.eventroute.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────
// Surfaces
// ─────────────────────────────────────────────────────────────────────────

/** Raised clay card. */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    backgroundColor: Color = ClaySurface,
    depth: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.clay(shape, backgroundColor, depth), content = content)
}

/** Recessed clay well (inputs, tracks, summary boxes). */
@Composable
fun ClayWell(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color = ClayInsetBg,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.clayInset(shape, color), content = content)
}

/** White rounded-top bar for sticky actions (event details, route planner). */
@Composable
fun ClayStickyBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clay(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), Color.White, depth = 14.dp)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Buttons, badges, tags
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun ClayPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ClayBluePrimary,
    textColor: Color = ClayBlueText,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed = rememberPressed(source)
    Row(
        modifier = modifier
            .clayPress(onClick, enabled, source)
            .clay(RoundedCornerShape(50), if (enabled) backgroundColor else ClayGrayChip, depth = 12.dp, pressed = pressed)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) textColor else ClayTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = ClayType.Button.copy(color = if (enabled) textColor else ClayTextMuted))
    }
}

/** Smaller pill used inside cards (View Ticket, Decline, View Details). */
@Composable
fun ClaySmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    textColor: Color = ClayTextPrimary,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed = rememberPressed(source)
    Box(
        modifier = modifier
            .clayPress(onClick, enabled, source)
            .clay(RoundedCornerShape(50), if (enabled) backgroundColor else ClayGrayChip, depth = 7.dp, pressed = pressed)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = ClayType.BodyStrong.copy(
                fontSize = 13.sp,
                color = if (enabled) textColor else ClayTextMuted
            )
        )
    }
}

/** Round clay icon button (back, close, share, bookmark). */
@Composable
fun ClayIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 44.dp,
    color: Color = Color.White,
    tint: Color = ClayTextPrimary,
) {
    val source = remember { MutableInteractionSource() }
    val pressed = rememberPressed(source)
    Box(
        modifier = modifier
            .size(size)
            .clayPress(onClick, true, source)
            .clay(CircleShape, color, depth = 7.dp, pressed = pressed),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

/** Icon tile: circle or squircle with a clay finish. */
@Composable
fun ClayIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = ClayBluePrimary,
    tint: Color = ClayBlueText,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier.size(size).clay(shape, background, depth = size * 0.16f),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ClayTag(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clay(RoundedCornerShape(50), backgroundColor, depth = 4.dp)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = ClayType.Label.copy(fontSize = 11.sp, letterSpacing = 0.3.sp, color = textColor))
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Inputs and segmented control
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun ClayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    uppercaseLabel: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = if (uppercaseLabel) label.uppercase() else label,
                style = if (uppercaseLabel) ClayType.Label.copy(fontSize = 11.sp, color = ClayTextSecondary)
                else ClayType.Caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayInset(RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = ClayTextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = ClayType.Body.copy(fontSize = 14.sp, color = ClayTextMuted))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = ClayType.BodyStrong,
                    cursorBrush = SolidColor(ClayBlueDeep),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

/** Recessed track with a raised white thumb. */
@Composable
fun ClaySegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayInset(RoundedCornerShape(50), ClaySurfaceSecondary, depth = 5.dp)
            .padding(5.dp)
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val source = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clayPress({ onSelect(i) }, true, source)
                    .then(if (selected) Modifier.clay(RoundedCornerShape(50), Color.White, depth = 6.dp) else Modifier)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = ClayType.BodyStrong.copy(
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) ClayBlueDeep else ClayTextMuted
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Avatars
// ─────────────────────────────────────────────────────────────────────────

/**
 * Looks up R.drawable.avatar_<firstname> at runtime (avatar_john, avatar_sarah, avatar_alex…).
 * Falls back to a pastel initials circle so a missing photo never crashes or shows the wrong face.
 */
@Composable
fun ClayAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    drawableRes: Int? = null,
    status: Color? = null,
) {
    val ctx = LocalContext.current
    val first = name.trim().split(" ").firstOrNull().orEmpty()
    val lookedUp = remember(name) {
        val key = first.lowercase().filter { it.isLetter() }
        ctx.resources.getIdentifier("avatar_$key", "drawable", ctx.packageName)
    }
    val resId: Int? = drawableRes ?: lookedUp.takeIf { it != 0 }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clay(CircleShape, Color.White, depth = size * 0.1f)
                .padding(size * 0.06f)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (resId != null) {
                Image(painterResource(resId), name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                val pastel = listOf(ClayBluePrimary, ClayPinkTag, ClayGreenPrimary, ClayAmberLight)[kotlin.math.abs(name.hashCode()) % 4]
                Box(Modifier.fillMaxSize().background(pastel), contentAlignment = Alignment.Center) {
                    Text(
                        first.take(1).uppercase(),
                        style = ClayType.Heading.copy(fontSize = (size.value * 0.4f).sp, color = ClayBlueText)
                    )
                }
            }
        }
        if (status != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(status)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Top bar / section header
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun ClayTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ClayIconButton(Icons.Rounded.ArrowBack, onBack, contentDescription = "Back")
        Spacer(Modifier.width(14.dp))
        Text(title, style = ClayType.Heading, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ClaySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = ClayType.Section)
        if (action != null) {
            val source = remember { MutableInteractionSource() }
            Text(
                action,
                style = ClayType.BodyStrong.copy(fontSize = 12.sp, color = ClayBlueDeep),
                modifier = Modifier.clayPress(onAction, true, source)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Event card (Explore list)
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun EventClayCard(
    event: Event,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier,
    bannerDrawableId: Int? = null
) {
    val isTeam = event.participationType.equals("team", ignoreCase = true)
    ClayCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), depth = 12.dp) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            if (bannerDrawableId != null) {
                Image(
                    painter = painterResource(bannerDrawableId),
                    contentDescription = event.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFFC7D2FE), ClayPinkTag, ClayBluePrimary))
                    )
                )
            }
            ClayTag(
                text = if (isTeam) "TEAM EVENT" else "SOLO EVENT",
                backgroundColor = if (isTeam) ClayPinkTag else ClayBluePrimary,
                textColor = if (isTeam) ClayPinkText else ClayBlueInk,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
            )
            event.travelEstimateMinutes?.let { minutes ->
                ClayTag(
                    text = "$minutes min",
                    backgroundColor = ClayGreenPrimary,
                    textColor = ClayGreenInk,
                    icon = Icons.Rounded.Schedule,
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    event.title,
                    style = ClayType.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (event.feeAmount == 0.0) "Free" else "₹${event.feeAmount.toInt()}",
                    style = ClayType.BodyStrong.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (event.feeAmount == 0.0) ClayGreenText else ClayBlueDeep
                    )
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalance, null, tint = ClayTextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(event.college.name, style = ClayType.Body.copy(fontSize = 13.sp))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClayAvatar("Sarah", size = 30.dp)
                    ClayAvatar("Alex", size = 30.dp, modifier = Modifier.offset(x = (-8).dp))
                    Box(
                        modifier = Modifier
                            .offset(x = (-14).dp)
                            .size(30.dp)
                            .clay(CircleShape, ClayGrayChip, depth = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+${event.seatsLeft}", style = ClayType.Caption.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                    }
                }

                ClaySmallButton(
                    text = "View Details",
                    onClick = onViewDetails,
                    backgroundColor = if (isTeam) ClayBluePrimary else ClayGreenPrimary,
                    textColor = if (isTeam) ClayBlueText else ClayGreenInk
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Bottom navigation
// ─────────────────────────────────────────────────────────────────────────

data class ClayNavTab(val key: String, val label: String, val icon: ImageVector)

val DefaultClayNavTabs = listOf(
    ClayNavTab("home", "Explore", Icons.Rounded.Home),
    ClayNavTab("my_events", "My Events", Icons.Rounded.EventAvailable),
    ClayNavTab("journey", "Journey", Icons.Rounded.Route),
    ClayNavTab("profile", "Profile", Icons.Rounded.Person),
)

/** Exactly four tabs, with the floating + button between tabs 2 and 3. */
@Composable
fun ClayBottomNavBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onCenterAction: () -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<ClayNavTab> = DefaultClayNavTabs,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clay(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp), Color.White, depth = 14.dp)
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayNavItem(tabs[0], selectedTab == tabs[0].key, { onTabSelected(tabs[0].key) }, Modifier.weight(1f))
            ClayNavItem(tabs[1], selectedTab == tabs[1].key, { onTabSelected(tabs[1].key) }, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
            ClayNavItem(tabs[2], selectedTab == tabs[2].key, { onTabSelected(tabs[2].key) }, Modifier.weight(1f))
            ClayNavItem(tabs[3], selectedTab == tabs[3].key, { onTabSelected(tabs[3].key) }, Modifier.weight(1f))
        }

        val source = remember { MutableInteractionSource() }
        val pressed = rememberPressed(source)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
                .size(54.dp)
                .clayPress(onCenterAction, true, source)
                .clay(CircleShape, ClayBluePrimary, depth = 12.dp, pressed = pressed),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, "Create event", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ClayNavItem(tab: ClayNavTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.clayPress(onClick, true, source).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            tab.icon, tab.label,
            tint = if (selected) ClayBlueDeep else ClayTextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = ClayType.Caption.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) ClayBlueDeep else ClayTextMuted
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
