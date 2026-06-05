package com.tools.screenshot3.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SamsungBlue80,
    secondary = SamsungBlueGrey80,
    tertiary = SamsungBlueAccent80
)

private val LightColorScheme = lightColorScheme(
    primary = SamsungBlue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF003262),
    secondary = SamsungBlueGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4F4),
    onSecondaryContainer = Color(0xFF182433),
    tertiary = SamsungBlueAccent40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E7FF),
    onTertiaryContainer = Color(0xFF0B2F66),
    background = Color(0xFFF8FAFF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDCE4F4),
    onSurfaceVariant = Color(0xFF445062),
    outline = Color(0xFF6C7482)
)

@Composable
fun Screenshot3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
        content = content
    )
}
