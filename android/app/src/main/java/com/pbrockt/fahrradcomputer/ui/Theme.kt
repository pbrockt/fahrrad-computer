package com.pbrockt.fahrradcomputer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dunkel mit hohem Kontrast: am Lenker zaehlt Ablesbarkeit auf einen Blick. Ein heller
 * Modus fuer pralle Sonne kommt mit dem Layout-Editor (Phase 2).
 */
object AppColors {
    val Bg = Color(0xFF0B0F14)
    val Panel = Color(0xFF141B23)
    val Line = Color(0xFF243040)
    val Text = Color(0xFFEEF2F5)
    val Dim = Color(0xFF8A97A3)
    val Accent = Color(0xFF3FA7D6)
    val Go = Color(0xFF46B97A)
    val Stop = Color(0xFFE5484D)
    val OnZone = Color(0xFF0B0F14)

    /** Z1 grau, Z2 blau, Z3 gruen, Z4 orange, Z5 rot - wie im Konzept festgelegt. */
    val Zones = listOf(
        Color(0xFF8A97A3),
        Color(0xFF3FA7D6),
        Color(0xFF46B97A),
        Color(0xFFF0973A),
        Color(0xFFE5484D),
    )

    fun zone(zone: Int): Color = Zones[(zone - 1).coerceIn(0, Zones.lastIndex)]
}

private val DarkScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.Bg,
    secondary = AppColors.Go,
    background = AppColors.Bg,
    onBackground = AppColors.Text,
    surface = AppColors.Panel,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Panel,
    onSurfaceVariant = AppColors.Dim,
    surfaceContainer = AppColors.Panel,
    outline = AppColors.Line,
    error = AppColors.Stop,
)

@Composable
fun FahrradTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
