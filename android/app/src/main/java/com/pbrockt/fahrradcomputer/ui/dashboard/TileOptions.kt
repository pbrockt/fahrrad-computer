package com.pbrockt.fahrradcomputer.ui.dashboard

/**
 * Einstellungen einer einzelnen Kachel (je Messwert): z. B. Wind relativ
 * oder absolut, Zeitraum eines Verlaufs. Gespeichert als Text im Layout, unbekannte Werte
 * fallen auf den Standard (= erste Wahl) zurueck.
 */
data class TileOption(val key: String, val label: String, val choices: List<Pair<String, String>>) {
    val default: String get() = choices.first().first
}

object TileOptions {
    val SPAN = TileOption("span", "Zeitraum", listOf("10" to "10 min", "1" to "1 min", "5" to "5 min", "20" to "20 min", "30" to "30 min", "60" to "60 min", "all" to "Ganze Fahrt"))
    val WIND_DIRECTION = TileOption("dir", "Richtung", listOf("rel" to "Relativ zur Fahrtrichtung", "abs" to "Absolut (Norden oben)"))
    val WIND_UNIT = TileOption("unit", "Stärke", listOf("kmh" to "km/h", "bft" to "Beaufort"))
    val HEADING_STYLE = TileOption("style", "Anzeige", listOf("arrow" to "Pfeil + Himmelsrichtung", "cardinal" to "Himmelsrichtung", "deg" to "Grad"))
    val PROFILE_ZOOM = TileOption("zoom", "Ausschnitt", listOf("all" to "Ganze Route", "rest" to "Von hier bis zum Ziel", "5" to "Nächste 5 km", "2" to "Nächste 2 km"))
    val ZONE_FILL = TileOption("fill", "Zonenfarbe", listOf("fill" to "Kachel füllen", "text" to "Nur die Zahl", "off" to "Keine"))
    val ZONE_BAR = TileOption("bar", "Zonenleiste", listOf("on" to "Anzeigen", "off" to "Ausblenden"))
    val MAP_ORIENTATION = TileOption("orient", "Ausrichtung", listOf("button" to "Wie der Kartenknopf", "north" to "Immer Norden oben", "heading" to "Immer Fahrtrichtung oben"))

    fun forType(type: TileType): List<TileOption> = when (type) {
        TileType.HR_CHART, TileType.POWER_CHART, TileType.SPEED_CHART -> listOf(SPAN)
        TileType.WIND -> listOf(WIND_DIRECTION, WIND_UNIT)
        TileType.HEADWIND -> listOf(WIND_UNIT)
        TileType.HEADING -> listOf(HEADING_STYLE)
        TileType.ELEVATION_PROFILE -> listOf(PROFILE_ZOOM)
        TileType.HEART_RATE, TileType.POWER -> listOf(ZONE_FILL)
        TileType.SPEED, TileType.CADENCE -> listOf(ZONE_BAR)
        TileType.ROUTE -> listOf(MAP_ORIENTATION)
        else -> emptyList()
    }
}

/** Gewaehlter Wert einer Option, sonst ihr Standard. */
fun Map<String, String>.opt(o: TileOption): String = this[o.key]?.takeIf { v -> o.choices.any { it.first == v } } ?: o.default
