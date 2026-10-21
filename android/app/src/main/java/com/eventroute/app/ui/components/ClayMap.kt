package com.eventroute.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventroute.app.ui.theme.*

/**
 * Stylised clay city map: pastel blocks, parks and a river on pale streets.
 * Placeholder until a real map SDK is wired in; pins and route overlays sit on top of it.
 */
@Composable
fun ClayMapBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFFF6F9FD))) {
        val w = size.width
        val h = size.height
        val gap = 12.dp.toPx()
        val cols = 5
        val cell = w / cols
        val rows = (h / cell).toInt() + 2
        val rnd = java.util.Random(11)

        for (r in 0 until rows) for (c in 0 until cols) {
            val x = c * cell + gap / 2
            val y = r * cell + gap / 2
            val bw = cell - gap - rnd.nextFloat() * cell * 0.18f
            val bh = cell - gap - rnd.nextFloat() * cell * 0.18f
            val roll = rnd.nextFloat()
            val color = when {
                roll < 0.16f -> Color(0xFFDDEBCB)   // park
                roll < 0.55f -> Color(0xFFE5EDF8)
                else -> Color(0xFFECF2FA)
            }
            drawRoundRect(color, Offset(x, y), Size(bw, bh), CornerRadius(14.dp.toPx()))
        }

        // River
        val river = Path().apply {
            moveTo(-30f, h * 0.70f)
            cubicTo(w * 0.30f, h * 0.52f, w * 0.60f, h * 0.92f, w + 30f, h * 0.60f)
        }
        drawPath(river, Color(0xFFCFE6FA), style = Stroke(width = w * 0.085f, cap = StrokeCap.Round))

        // Main avenue
        val avenue = Path().apply {
            moveTo(-20f, h * 0.20f)
            lineTo(w + 20f, h * 0.42f)
        }
        drawPath(avenue, Color.White, style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Thick clay route: white casing, blue core, optional dashed remainder. */
@Composable
fun ClayRouteLine(
    points: List<Offset>,           // fractions of the container, 0f..1f
    modifier: Modifier = Modifier,
    color: Color = ClayBlueDeep,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(points[0].x * size.width, points[0].y * size.height)
            for (i in 1 until points.size) {
                val p = points[i]
                val prev = points[i - 1]
                val mx = (prev.x + p.x) / 2f * size.width
                val my = (prev.y + p.y) / 2f * size.height
                quadraticBezierTo(prev.x * size.width, prev.y * size.height, mx, my)
            }
            lineTo(points.last().x * size.width, points.last().y * size.height)
        }
        drawPath(path, Color.White, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(
            path, Color.White.copy(alpha = 0.7f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f)))
        )
    }
}

/** Event pin: category + name chip over a clay pin. */
@Composable
fun ClayMapPin(
    category: String,
    name: String,
    accent: Color,
    pinColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .clay(RoundedCornerShape(16.dp), Color.White, depth = 8.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(category, style = ClayType.Label.copy(fontSize = 9.sp, color = accent))
            Text(name, style = ClayType.BodyStrong.copy(fontSize = 12.sp))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.size(34.dp).clay(CircleShape, pinColor, depth = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Teammate marker: status chip over an avatar. */
@Composable
fun ClayTeammateMarker(
    name: String,
    chipText: String,
    chipBg: Color,
    chipTextColor: Color,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 40.dp,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            chipText,
            style = ClayType.BodyStrong.copy(fontSize = 11.sp, color = chipTextColor),
            modifier = Modifier
                .clay(RoundedCornerShape(50), chipBg, depth = 5.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(6.dp))
        ClayAvatar(name, size = avatarSize)
    }
}
