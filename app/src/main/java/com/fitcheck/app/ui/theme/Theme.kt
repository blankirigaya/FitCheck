package com.fitcheck.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = OffWhite,
    secondary = Accent,
    onSecondary = OffWhite,
    tertiary = AccentSoft,
    onTertiary = Ink,
    background = OffWhite,
    onBackground = Ink,
    surface = OffWhite,
    onSurface = Ink,
    surfaceVariant = OffWhiteElevated,
    onSurfaceVariant = InkSoft,
    outline = Hairline,
    outlineVariant = HairlineSoft,
    error = Danger,
    onError = OffWhite
)

private val DarkColors = darkColorScheme(
    primary = NightInk,
    onPrimary = NightBackground,
    secondary = NightAccent,
    onSecondary = NightBackground,
    tertiary = NightAccent,
    onTertiary = NightBackground,
    background = NightBackground,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceElevated,
    onSurfaceVariant = NightInkSoft,
    outline = NightHairline,
    outlineVariant = NightHairline,
    error = Danger,
    onError = NightInk
)

@Composable
fun FitCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FitCheckTypography,
        content = content
    )
}
