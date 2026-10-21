package com.eventroute.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClayLightColorScheme = lightColorScheme(
    primary = ClayBlueDeep,
    onPrimary = Color.White,
    primaryContainer = ClayBluePrimary,
    onPrimaryContainer = ClayBlueText,
    secondary = ClayPinkButton,
    onSecondary = Color.White,
    secondaryContainer = ClayPinkPrimary,
    onSecondaryContainer = ClayPinkText,
    tertiary = ClayAmberButton,
    onTertiary = ClayAmberInk,
    tertiaryContainer = ClayAmberLight,
    onTertiaryContainer = ClayAmberText,
    background = ClayBackground,
    onBackground = ClayTextPrimary,
    surface = ClaySurface,
    onSurface = ClayTextPrimary,
    surfaceVariant = ClayInsetBg,
    onSurfaceVariant = ClayTextSecondary,
    outline = ClayCardBorder,
    error = ClayAlertPinkText
)

/** Claymorphism is a light-only language, so the theme is intentionally not dark-aware. */
@Composable
fun EventRouteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClayLightColorScheme,
        typography = Typography,
        content = content
    )
}
