package com.eventroute.app.ui.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.eventroute.app.ui.theme.ClayShadowBase
import com.eventroute.app.ui.theme.ClaySurface

/**
 * Claymorphism engine.
 *
 * A clay surface = soft tinted drop shadow (bottom-right) + white light bloom (top-left)
 * + the fill + an inner highlight (top-left) + an inner shade (bottom-right).
 * That inner pair is what makes it look puffy instead of flat.
 *
 * Exact blurred shadows need API 28+. Below that we fall back to Modifier.shadow().
 */
fun Modifier.clay(
    shape: Shape = RoundedCornerShape(28.dp),
    color: Color = ClaySurface,
    depth: Dp = 10.dp,
    pressed: Boolean = false,
): Modifier {
    val d = if (pressed) depth * 0.45f else depth
    val hasBlur = Build.VERSION.SDK_INT >= 28
    val base = if (hasBlur) this else this.shadow(
        elevation = d / 2,
        shape = shape,
        ambientColor = ClayShadowBase.copy(alpha = 0.35f),
        spotColor = ClayShadowBase.copy(alpha = 0.45f)
    )
    return base
        .drawWithCache {
            val path = shape.toPath(size, layoutDirection, this)
            val blur = d.toPx() * 0.5f
            val off = d.toPx() * 0.42f
            val inner = d.toPx() * 0.28f
            val pad = d.toPx() * 4f
            val outerShadow = lerp(color, ClayShadowBase, 0.45f).copy(alpha = 0.42f)
            val innerShade = lerp(color, ClayShadowBase, 0.55f).copy(alpha = 0.38f)
            val inverse = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(-pad, -pad, size.width + pad, size.height + pad))
                addPath(path)
            }
            val androidPath = path.asAndroidPath()
            val androidInverse = inverse.asAndroidPath()
            val p1 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val p2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            onDrawWithContent {
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    if (hasBlur) {
                        // outer drop shadow (down-right) + light bloom (up-left)
                        p1.color = color.copy(alpha = 1f).toArgb()
                        p1.setShadowLayer(blur, off, off, outerShadow.toArgb())
                        nc.drawPath(androidPath, p1)
                        p2.color = color.copy(alpha = 1f).toArgb()
                        p2.setShadowLayer(blur, -off, -off, Color.White.copy(alpha = 0.95f).toArgb())
                        nc.drawPath(androidPath, p2)
                    }
                }
                // fill
                drawPath(path, color)
                // inner light + inner shade, clipped to the shape
                if (hasBlur) {
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        nc.save()
                        nc.clipPath(androidPath)
                        p1.color = android.graphics.Color.BLACK
                        p1.setShadowLayer(blur * 0.8f, inner, inner, Color.White.copy(alpha = 0.9f).toArgb())
                        nc.drawPath(androidInverse, p1)
                        p2.color = android.graphics.Color.BLACK
                        p2.setShadowLayer(blur * 0.8f, -inner, -inner, innerShade.toArgb())
                        nc.drawPath(androidInverse, p2)
                        nc.restore()
                    }
                }
                drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.dp.toPx()))
                drawContent()
            }
        }
        .clip(shape)
}

/** Recessed clay: the inverse lighting of [clay]. Used for inputs, tracks and wells. */
fun Modifier.clayInset(
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color = Color(0xFFF3F6FB),
    depth: Dp = 6.dp,
): Modifier = this
    .drawWithCache {
        val path = shape.toPath(size, layoutDirection, this)
        val blur = depth.toPx()
        val inner = depth.toPx() * 0.6f
        val pad = depth.toPx() * 4f
        val inverse = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-pad, -pad, size.width + pad, size.height + pad))
            addPath(path)
        }
        val androidPath = path.asAndroidPath()
        val androidInverse = inverse.asAndroidPath()
        val dark = lerp(color, ClayShadowBase, 0.5f).copy(alpha = 0.40f)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val hasBlur = Build.VERSION.SDK_INT >= 28

        onDrawWithContent {
            drawPath(path, color)
            if (hasBlur) {
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.clipPath(androidPath)
                    p.color = android.graphics.Color.BLACK
                    p.setShadowLayer(blur, inner, inner, dark.toArgb())
                    nc.drawPath(androidInverse, p)
                    p.setShadowLayer(blur, -inner, -inner, Color.White.copy(alpha = 0.95f).toArgb())
                    nc.drawPath(androidInverse, p)
                    nc.restore()
                }
            }
            drawContent()
        }
    }
    .clip(shape)

/** Squishy press feedback: scales down and flattens the clay while held. */
@Composable
fun Modifier.clayPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(stiffness = 600f), label = "clayPress")
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun rememberPressed(source: MutableInteractionSource): Boolean {
    val pressed by source.collectIsPressedAsState()
    return pressed
}

private fun Shape.toPath(size: androidx.compose.ui.geometry.Size, dir: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Path {
    val path = Path()
    when (val o = createOutline(size, dir, density)) {
        is Outline.Rectangle -> path.addRect(o.rect)
        is Outline.Rounded -> path.addRoundRect(o.roundRect)
        is Outline.Generic -> path.addPath(o.path)
    }
    return path
}
