package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pbrockt.fahrradcomputer.R

enum class ThemeMode(val label: String) {
    DARK("Dunkel"),
    LIGHT("Hell (Sonne)"),
    SYSTEM("Wie System"),
}

/**
 * Hervorhebungsfarbe: Knoepfe, Leiste unten, Banner,
 * aktiver Menue-Eintrag. [light] fuer hellen, [dark] fuer schwarzen Grund.
 */
enum class AccentColor(val label: String, val light: Long, val dark: Long) {
    RED("Rot", 0xFFC8102E, 0xFFE3141B),
    PINK("Pink", 0xFFD81B60, 0xFFF0287A),
    MAGENTA("Magenta", 0xFFAD1AAD, 0xFFCC33CC),
    PURPLE("Lila", 0xFF6A2BC2, 0xFF8A4FE0),
    ROYAL_BLUE("Königsblau", 0xFF2942B8, 0xFF4A63E0),
    BLUE("Blau", 0xFF0B6FB8, 0xFF2E90E0),
    AQUA("Türkis", 0xFF0097A7, 0xFF19B5C8),
    GREEN("Grün", 0xFF2B8A3E, 0xFF3BA552),
    LIME("Limette", 0xFF6B9A0A, 0xFF8CBF1A),
    GOLD("Gold", 0xFFB8860B, 0xFFD9A21B),
    ORANGE("Orange", 0xFFE06A00, 0xFFF57C12),
    GRAPHITE("Graphit", 0xFF3A3A3A, 0xFF6A6A6A),
}

/**
 * Schmale Schrift fuer alles - fuer grosse Ziffern. Barlow Condensed
 * steht unter der SIL Open Font License (assets/OFL-BarlowCondensed.txt).
 */
val Condensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Normal),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_semibold, FontWeight.Bold),
)

/**
 * Farben: schwarzer (bzw. weisser) Grund, Kacheln nur durch feine Linien
 * getrennt, ein kraeftiges Rot fuer Bedienung, und jede Messgroesse in ihrer eigenen Farbe.
 * Die Zonenfarben bleiben in beiden Modi gleich, damit man eine Zone immer wiedererkennt.
 */
data class Palette(
    val isDark: Boolean,
    val Bg: Color,
    val Panel: Color,
    val Line: Color,
    val Text: Color,
    val Dim: Color,
    /** Die Hervorhebungsfarbe: Knoepfe, Leiste unten, Hervorhebungen. */
    val Accent: Color,
    val Go: Color = Color(0xFF2E9E44),
    val Stop: Color = Color(0xFFD7141A),
    /** Schrift auf farbigem Grund (Zonen, Knoepfe). */
    val OnZone: Color = Color.White,
    // Farben je Messgroesse.
    val Speed: Color,
    val Distance: Color,
    val Cadence: Color,
    val Calories: Color = Color(0xFFE8590C),
    val Altitude: Color,
    val Grade: Color = Color(0xFFE53935),
) {
    /** Dunklere Stufe der Hervorhebungsfarbe - fuer Felder neben "Start". */
    val AccentDark: Color get() = Color(Accent.red * 0.65f, Accent.green * 0.65f, Accent.blue * 0.65f)
    /** Z1 grau, Z2 blau, Z3 gruen, Z4 orange, Z5 rot. */
    val Zones = listOf(
        Color(0xFF8E8E93),
        Color(0xFF1C6EA4),
        Color(0xFF2E9E44),
        Color(0xFFF08A00),
        Color(0xFFD7141A),
    )

    fun zone(zone: Int): Color = Zones[(zone - 1).coerceIn(0, Zones.lastIndex)]

    companion object {
        val Dark = Palette(
            isDark = true,
            Bg = Color(0xFF000000),
            Panel = Color(0xFF161616),
            Line = Color(0xFF333333),
            Text = Color(0xFFFFFFFF),
            Dim = Color(0xFF9A9A9A),
            Accent = Color(0xFFE3141B),
            Speed = Color(0xFF2E9BF0),
            Distance = Color(0xFF3DBB5C),
            Cadence = Color(0xFFF7941D),
            Altitude = Color(0xFFA48BF5),
        )

        val Light = Palette(
            isDark = false,
            Bg = Color(0xFFFFFFFF),
            Panel = Color(0xFFF2F2F2),
            Line = Color(0xFFD0D0D0),
            Text = Color(0xFF000000),
            Dim = Color(0xFF5E5E5E),
            Accent = Color(0xFFC8102E),
            Speed = Color(0xFF0B7BD0),
            Distance = Color(0xFF2B8A3E),
            Cadence = Color(0xFFE67E00),
            Altitude = Color(0xFF6A4FD8),
        )
    }
}

private val LocalPalette = staticCompositionLocalOf { Palette.Dark }

/** Die aktuell gueltige Palette. */
val AppColors: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Material-Textstile mit der schmalen Schrift, eine Spur groesser - sie laeuft enger. */
private val AppTypography: Typography = Typography().let { t ->
    fun TextStyle.c() = copy(fontFamily = Condensed, fontSize = fontSize * 1.08f, lineHeight = lineHeight * 1.05f)
    Typography(
        displayLarge = t.displayLarge.c(), displayMedium = t.displayMedium.c(), displaySmall = t.displaySmall.c(),
        headlineLarge = t.headlineLarge.c(), headlineMedium = t.headlineMedium.c(), headlineSmall = t.headlineSmall.c(),
        titleLarge = t.titleLarge.c(), titleMedium = t.titleMedium.c(), titleSmall = t.titleSmall.c(),
        bodyLarge = t.bodyLarge.c(), bodyMedium = t.bodyMedium.c(), bodySmall = t.bodySmall.c(),
        labelLarge = t.labelLarge.c(), labelMedium = t.labelMedium.c(), labelSmall = t.labelSmall.c(),
    )
}

@Composable
fun FahrradTheme(mode: ThemeMode = ThemeMode.DARK, accent: AccentColor = AccentColor.RED, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = (if (dark) Palette.Dark else Palette.Light).let { base ->
        base.copy(Accent = Color(if (dark) accent.dark else accent.light))
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.Accent, onPrimary = Color.White, secondary = p.Go,
            primaryContainer = p.Accent, onPrimaryContainer = Color.White,
            secondaryContainer = p.Panel, onSecondaryContainer = p.Text,
            background = p.Bg, onBackground = p.Text,
            surface = p.Bg, onSurface = p.Text,
            surfaceVariant = p.Panel, onSurfaceVariant = p.Dim, surfaceContainer = p.Panel,
            surfaceContainerLow = p.Panel, surfaceContainerHigh = p.Panel, surfaceContainerHighest = p.Panel,
            outline = p.Line, outlineVariant = p.Line, error = p.Stop,
        )
    } else {
        lightColorScheme(
            primary = p.Accent, onPrimary = Color.White, secondary = p.Go,
            primaryContainer = p.Accent, onPrimaryContainer = Color.White,
            secondaryContainer = p.Panel, onSecondaryContainer = p.Text,
            background = p.Bg, onBackground = p.Text,
            surface = p.Bg, onSurface = p.Text,
            surfaceVariant = p.Panel, onSurfaceVariant = p.Dim, surfaceContainer = p.Panel,
            surfaceContainerLow = p.Panel, surfaceContainerHigh = p.Panel, surfaceContainerHighest = p.Panel,
            outline = p.Line, outlineVariant = p.Line, error = p.Stop,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
