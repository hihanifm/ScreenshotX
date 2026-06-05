package com.tools.screenshot3.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = SamsungBlue80,
    secondary = SamsungBlueGrey80,
    tertiary = SamsungBlueAccent80
)

private val LightColorScheme = lightColorScheme(
    primary = SamsungBlue40,
    onPrimary = Color.White,
    primaryContainer = SamsungBlueSubtle,
    onPrimaryContainer = Color(0xFF002D6B),
    secondary = SamsungBlueMedium,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EDFA),
    onSecondaryContainer = Color(0xFF1A3A5C),
    tertiary = SamsungBlueAccent40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E7FF),
    onTertiaryContainer = Color(0xFF0B2F66),
    background = SamsungSurfaceBase,
    onBackground = SamsungTextPrimary,
    surface = SamsungSurfaceCard,
    onSurface = SamsungTextPrimary,
    surfaceVariant = SamsungSurfaceElevated,
    onSurfaceVariant = SamsungTextSecondary,
    outline = Color(0xFFD1D9E6),
    outlineVariant = Color(0xFFE8EDF5),
    errorContainer = SamsungRedSoft,
    onErrorContainer = SamsungRedText
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun Screenshot3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
