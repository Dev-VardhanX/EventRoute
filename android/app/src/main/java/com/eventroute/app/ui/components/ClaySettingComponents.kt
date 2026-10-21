package com.eventroute.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.ui.theme.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Custom ClaySwitch adhering to EventRoute Claymorphism styling.
 * Blue when active, recessed inset gray when off.
 */
@Composable
fun ClaySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackWidth = 56.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val padding = 4.dp

    val targetOffset = if (checked) trackWidth - thumbSize - padding else padding
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = tween(durationMillis = 200),
        label = "ClaySwitchThumbOffset"
    )

    val trackColor = if (checked) ClayBluePrimary else ClayInsetBg
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clayPress({ onCheckedChange(!checked) }, enabled = true, source = interactionSource)
            .then(
                if (checked) {
                    Modifier.clay(shape, trackColor, depth = 4.dp)
                } else {
                    Modifier.clayInset(shape, trackColor)
                }
            )
            .padding(vertical = padding),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clay(CircleShape, Color.White, depth = 4.dp)
        )
    }
}

/**
 * Custom ClayTimeChip for selecting quiet hours times.
 */
@Composable
fun ClayTimeChip(
    label: String,
    time: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val formatter = remember { DateTimeFormatter.ofPattern("hh:mm a") }
    val formattedTime = time.format(formatter)

    Column(
        modifier = modifier
            .clayPress(onClick, true, source)
            .clay(RoundedCornerShape(20.dp), Color.White, depth = 7.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = ClayType.Label.copy(fontSize = 9.sp, color = ClayBlueDeep)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formattedTime,
            style = ClayType.BodyStrong.copy(fontSize = 15.sp, color = ClayTextPrimary)
        )
    }
}
