package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    DARK("Dunkel"),
    LIGHT("Hell (Sonne)"),
    SYSTEM("Wie System"),
}

/**
 * Farben der App. Dunkel ist Standard; Hell ist fuer pralle Sonne gedacht, wo ein dunkles
 * Display kaum ablesbar ist. Die Zonenfarben bleiben in beiden Modi gleich, damit man
 * eine Zone immer an derselben Farbe erkennt.
 */
data class Palette(
    val isDark: Boolean,
    val Bg: Color,
    val Panel: Color,
    val Line: Color,
    val Text: Color,
    val Dim: Color,
    val Accent: Color,
    val Go: Color = Color(0xFF46B97A),
    val Stop: Color = Color(0xFFE5484D),
    /** Schrift auf farbigem Grund (Zonen, Knoepfe). */
    val OnZone: Color = Color(0xFF0B0F14),
) {
    /** Z1 grau, Z2 blau, Z3 gruen, Z4 orange, Z5 rot - wie im Konzept festgelegt. */
    val Zones = listOf(
        Color(0xFF8A97A3),
        Color(0xFF3FA7D6),
        Color(0xFF46B97A),
        Color(0xFFF0973A),
        Color(0xFFE5484D),
    )

    fun zone(zone: Int): Color = Zones[(zone - 1).coerceIn(0, Zones.lastIndex)]

    companion object {
        val Dark = Palette(
            isDark = true,
            Bg = Color(0xFF0B0F14),
            Panel = Color(0xFF141B23),
            Line = Color(0xFF243040),
            Text = Color(0xFFEEF2F5),
            Dim = Color(0xFF8A97A3),
            Accent = Color(0xFF3FA7D6),
        )

        val Light = Palette(
            isDark = false,
            Bg = Color(0xFFE9EDF1),
            Panel = Color(0xFFFFFFFF),
            Line = Color(0xFFC9D1D9),
            Text = Color(0xFF0B0F14),
            Dim = Color(0xFF4F5B66),
            Accent = Color(0xFF1C7FB0),
        )
    }
}

private val LocalPalette = staticCompositionLocalOf { Palette.Dark }

/** Die aktuell gueltige Palette. */
val AppColors: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

@Composable
fun FahrradTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = if (dark) Palette.Dark else Palette.Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.Accent, onPrimary = p.Bg, secondary = p.Go,
            background = p.Bg, onBackground = p.Text,
            surface = p.Panel, onSurface = p.Text,
            surfaceVariant = p.Panel, onSurfaceVariant = p.Dim, surfaceContainer = p.Panel,
            outline = p.Line, error = p.Stop,
        )
    } else {
        lightColorScheme(
            primary = p.Accent, onPrimary = Color.White, secondary = p.Go,
            background = p.Bg, onBackground = p.Text,
            surface = p.Panel, onSurface = p.Text,
            surfaceVariant = p.Panel, onSurfaceVariant = p.Dim, surfaceContainer = p.Panel,
            outline = p.Line, error = p.Stop,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
