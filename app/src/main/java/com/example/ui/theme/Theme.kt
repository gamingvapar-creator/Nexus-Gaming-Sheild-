package com.example.ui.theme

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

val Purple = Color(0xFF8B5CF6)
val Pink = Color(0xFFD946EF)
val Background = Color(0xFF000000)
val Panel = Color(0xFF121214)

private val DarkColorScheme = darkColorScheme(
    primary = Purple,
    secondary = Pink,
    background = Background,
    surface = Panel,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Always dark theme
    dynamicColor: Boolean = false, // Disable dynamic color for custom theme
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
