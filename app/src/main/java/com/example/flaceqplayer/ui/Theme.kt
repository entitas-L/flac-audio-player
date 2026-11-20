package com.example.flaceqplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0A0C13)
val SurfaceDark = Color(0xFF141825)
val SurfaceElevated = Color(0xFF1C2133)
val TextPrimary = Color(0xFFF3F5FA)
val TextMuted = Color(0xFF98A2B8)
val RomajiBlue = Color(0xFF9FB7FF)
val FuriganaTint = Color(0xFFB7C3E8)
val TranslationMint = Color(0xFF8EE3BC)

@Composable
fun FlacPlayerTheme(
    accent: Color = Color(0xFF7C6CFF),
    content: @Composable () -> Unit
) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = Color(0xFFE85D9E),
        onSecondary = Color.White,
        background = BgDark,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceElevated,
        onSurfaceVariant = TextMuted
    )
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return "$m:${s.toString().padStart(2, '0')}"
}
