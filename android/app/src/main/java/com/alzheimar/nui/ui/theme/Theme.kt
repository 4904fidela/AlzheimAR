package com.alzheimar.nui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF08090C)
val SlateSurface = Color(0xFF161922)
val EmeraldGreen = Color(0xFF00A86B)
val EmeraldLight = Color(0xFF10E395)
val EmeraldDark = Color(0xFF007348)
val SosRed = Color(0xFFEF4444)
val TextLight = Color(0xFFF3F4F6)
val TextMuted = Color(0xFF9CA3AF)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    secondary = EmeraldLight,
    tertiary = EmeraldDark,
    background = ObsidianBg,
    surface = SlateSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = TextLight,
    onSurface = TextLight,
    error = SosRed
)

@Composable
fun AlzheimARTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
