package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CrimsonPulse,
    onPrimary = Color.White,
    primaryContainer = CrimsonPulseSoft,
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = LiveEmerald,
    onSecondary = Color(0xFF002418),
    secondaryContainer = LiveEmeraldSoft,
    onSecondaryContainer = Color(0xFF96FFE0),
    tertiary = TelemetrySky,
    onTertiary = Color(0xFF001F2E),
    tertiaryContainer = TelemetrySkySoft,
    onTertiaryContainer = Color(0xFFC2EBFF),
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioBorder,
    outlineVariant = StudioBorder.copy(alpha = 0.6f),
    error = Color(0xFFFF5C77),
    onError = Color.White,
)

private val StudioLightColorScheme = lightColorScheme(
    primary = CrimsonPulseLight,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainerLight,
    onPrimaryContainer = Color(0xFF41000C),
    secondary = LiveEmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = LiveEmeraldContainerLight,
    onSecondaryContainer = Color(0xFF00291B),
    tertiary = TelemetrySkyLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6F0FF),
    onTertiaryContainer = Color(0xFF001E2E),
    background = LightStudioCanvas,
    onBackground = LightTextPrimary,
    surface = LightStudioSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightStudioSurfaceAlt,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = LightStudioSurface,
    surfaceContainer = LightStudioSurfaceAlt,
    surfaceContainerHigh = Color(0xFFE0E7F2),
    outline = LightStudioBorder,
    outlineVariant = LightStudioBorder.copy(alpha = 0.65f),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

val TubePulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

enum class ThemePreference {
    DARK,
    LIGHT,
    SYSTEM
}

@Composable
fun TubePulseTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> systemDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> StudioDarkColorScheme
        else -> StudioLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TubePulseTypography,
        shapes = TubePulseShapes,
        content = content,
    )
}
