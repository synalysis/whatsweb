package com.synalysis.whatsweb.ui.theme

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

private val Spruce = Color(0xFF1F4E46)
private val SpruceContainer = Color(0xFFD5EBE4)
private val Ink = Color(0xFF1C2421)
private val Paper = Color(0xFFF3F1EC)
private val PaperRaised = Color(0xFFFBFAF7)

private val LightColors = lightColorScheme(
    primary = Spruce,
    onPrimary = Color(0xFFF3FBF8),
    primaryContainer = SpruceContainer,
    onPrimaryContainer = Color(0xFF06221C),
    secondary = Color(0xFF5C5346),
    onSecondary = Color(0xFFFFFBF6),
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4E0D6),
    onSurfaceVariant = Color(0xFF4A524E),
    outline = Color(0xFF6F7A74),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCFC2),
    onPrimary = Color(0xFF0E2420),
    primaryContainer = Color(0xFF1B3A34),
    onPrimaryContainer = Color(0xFFD5EBE4),
    secondary = Color(0xFFD2C6B4),
    onSecondary = Color(0xFF2A241C),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE4EBE7),
    surface = Color(0xFF1A211E),
    onSurface = Color(0xFFE4EBE7),
    surfaceVariant = Color(0xFF2A3330),
    onSurfaceVariant = Color(0xFFC2CCC7),
    outline = Color(0xFF8C9993),
)

@Composable
fun WhatswebTheme(
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
