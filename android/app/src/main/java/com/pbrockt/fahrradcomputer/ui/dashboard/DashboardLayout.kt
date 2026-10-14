package com.pbrockt.fahrradcomputer.ui.dashboard

/** Was eine Kachel anzeigen kann. */
enum class TileType(val label: String) {
    SPEED("Geschwindigkeit"),
    HEART_RATE("Puls"),
    CADENCE("Trittfrequenz"),
    POWER("Leistung"),
    CALORIES("Kalorien"),
    DISTANCE("Distanz"),
    MOVING_TIME("Fahrzeit"),
    AVG_SPEED("Ø Geschwindigkeit"),
    MAX_SPEED("Max. Geschwindigkeit"),
    AVG_HR("Ø Puls"),
    MAX_HR("Max. Puls"),
    AVG_CADENCE("Ø Trittfrequenz"),
    AVG_POWER("Ø Leistung"),
    ALTITUDE("Höhe"),
    CLOCK("Uhrzeit"),
    ROUTE("Karte / Navigation"),
    ROUTE_REMAINING("Rest der Route"),
    TURN("Abbiegehinweis"),
}

/** Zeilenhoehe als Anteil am Bildschirm. */
enum class RowHeight(val label: String, val weight: Float) {
    SMALL("klein", 0.8f),
    MEDIUM("mittel", 1.2f),
    LARGE("groß", 1.6f),
    XLARGE("sehr groß", 2.6f);

    fun next(): RowHeight = entries[(ordinal + 1) % entries.size]
}

/** Eine Kachel; eine breite Kachel belegt zwei Einheiten der Zeile. */
data class TileSlot(val type: TileType, val wide: Boolean = false) {
    val units: Int get() = if (wide) 2 else 1
}

data class TileRow(val tiles: List<TileSlot>, val height: RowHeight = RowHeight.MEDIUM) {
    val units: Int get() = tiles.sumOf { it.units }
}

data class DashboardPage(val name: String, val rows: List<TileRow>)

/**
 * Das Dashboard als Daten: Seiten aus Zeilen aus Kacheln. Der Editor veraendert nur diese
 * Struktur ([LayoutEditor]), gespeichert wird sie als JSON ([LayoutCodec]).
 */
data class DashboardLayout(val pages: List<DashboardPage>) {

    companion object {
        /** Mehr als drei Einheiten pro Zeile werden am Handy zu klein zum Ablesen. */
        const val MAX_ROW_UNITS = 3

        private fun row(height: RowHeight, vararg tiles: TileType) =
            TileRow(tiles.map { TileSlot(it) }, height)

        val DEFAULT = DashboardLayout(
            listOf(
                DashboardPage(
                    "Fahrt",
                    listOf(
                        row(RowHeight.LARGE, TileType.SPEED),
                        row(RowHeight.MEDIUM, TileType.HEART_RATE, TileType.CADENCE),
                        row(RowHeight.MEDIUM, TileType.DISTANCE, TileType.MOVING_TIME),
                        row(RowHeight.SMALL, TileType.AVG_SPEED, TileType.CALORIES, TileType.CLOCK),
                        // Platzhalter fuer die Navigation: bis zur Karte (Phase 6) die Streckenskizze.
                        row(RowHeight.LARGE, TileType.ROUTE),
                    ),
                ),
                // Fuer Fahrten mit Route: Abbiegehinweis gross, darunter die Karte.
                DashboardPage(
                    "Navi",
                    listOf(
                        row(RowHeight.LARGE, TileType.TURN),
                        row(RowHeight.XLARGE, TileType.ROUTE),
                        row(RowHeight.SMALL, TileType.SPEED, TileType.ROUTE_REMAINING, TileType.CLOCK),
                    ),
                ),
                DashboardPage(
                    "Puls",
                    listOf(
                        row(RowHeight.LARGE, TileType.HEART_RATE),
                        row(RowHeight.MEDIUM, TileType.AVG_HR, TileType.MAX_HR),
                        row(RowHeight.MEDIUM, TileType.CADENCE, TileType.POWER),
                        row(RowHeight.MEDIUM, TileType.CALORIES, TileType.MOVING_TIME),
                    ),
                ),
                DashboardPage(
                    "Übersicht",
                    listOf(
                        row(RowHeight.MEDIUM, TileType.DISTANCE, TileType.MOVING_TIME),
                        row(RowHeight.MEDIUM, TileType.AVG_SPEED, TileType.MAX_SPEED),
                        row(RowHeight.MEDIUM, TileType.AVG_HR, TileType.MAX_HR),
                        row(RowHeight.MEDIUM, TileType.AVG_CADENCE, TileType.AVG_POWER),
                        row(RowHeight.MEDIUM, TileType.CALORIES, TileType.ALTITUDE, TileType.CLOCK),
                    ),
                ),
            )
        )
    }
}
