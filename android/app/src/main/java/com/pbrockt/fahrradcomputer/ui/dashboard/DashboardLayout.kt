package com.pbrockt.fahrradcomputer.ui.dashboard

/** Gruppen im Kachel-Auswahldialog. */
enum class TileCategory(val label: String) {
    SPEED("Tempo"),
    TIME("Distanz & Zeit"),
    HEART("Puls"),
    POWER("Leistung"),
    CADENCE("Trittfrequenz"),
    ELEVATION("Höhe & Anstiege"),
    NAVIGATION("Karte & Navigation"),
    WEATHER("Wetter & Umgebung"),
    OTHER("Sonstiges"),
}

/** Was eine Kachel anzeigen kann. Die Reihenfolge hier ist egal - gespeichert wird der Name. */
enum class TileType(val label: String, val category: TileCategory) {
    SPEED("Tempo", TileCategory.SPEED),
    AVG_SPEED("Ø Tempo", TileCategory.SPEED),
    MAX_SPEED("Max. Tempo", TileCategory.SPEED),
    SPEED_CHART("Tempoverlauf (10 min)", TileCategory.SPEED),

    DISTANCE("Distanz", TileCategory.TIME),
    MOVING_TIME("Fahrzeit", TileCategory.TIME),
    ELAPSED_TIME("Gesamtzeit (mit Pausen)", TileCategory.TIME),
    CLOCK("Uhrzeit", TileCategory.TIME),
    CALORIES("Kalorien", TileCategory.TIME),

    HEART_RATE("Puls", TileCategory.HEART),
    AVG_HR("Ø Puls", TileCategory.HEART),
    MAX_HR("Max. Puls", TileCategory.HEART),
    HR_CHART("Pulsverlauf (10 min)", TileCategory.HEART),
    HR_ZONE_CHART("Pulszonen-Diagramm", TileCategory.HEART),

    POWER("Leistung", TileCategory.POWER),
    AVG_POWER("Ø Leistung", TileCategory.POWER),
    POWER_KG("Leistung pro kg", TileCategory.POWER),
    POWER_FTP("Leistung in % FTP", TileCategory.POWER),
    POWER_CHART("Leistungsverlauf (10 min)", TileCategory.POWER),
    POWER_ZONE_CHART("Leistungszonen-Diagramm", TileCategory.POWER),

    CADENCE("Trittfrequenz", TileCategory.CADENCE),
    AVG_CADENCE("Ø Trittfrequenz", TileCategory.CADENCE),

    ALTITUDE("Höhe", TileCategory.ELEVATION),
    GRADE("Steigung", TileCategory.ELEVATION),
    ASCENT("Anstieg (gefahren)", TileCategory.ELEVATION),
    DESCENT("Abstieg (gefahren)", TileCategory.ELEVATION),
    VAM("VAM (Höhenmeter pro Stunde)", TileCategory.ELEVATION),
    CLIMB("Anstieg: aktuell / nächster", TileCategory.ELEVATION),
    ELEVATION_PROFILE("Höhenprofil der Route", TileCategory.ELEVATION),
    ROUTE_ASCENT_LEFT("Rest-Höhenmeter der Route", TileCategory.ELEVATION),

    ROUTE("Karte", TileCategory.NAVIGATION),
    TURN("Abbiegehinweis", TileCategory.NAVIGATION),
    ROUTE_REMAINING("Rest der Route", TileCategory.NAVIGATION),
    ETA("Ankunft (Uhrzeit & Restzeit)", TileCategory.NAVIGATION),
    HEADING("Fahrtrichtung", TileCategory.NAVIGATION),

    WIND("Wind", TileCategory.WEATHER),
    HEADWIND("Gegenwind", TileCategory.WEATHER),
    TEMPERATURE("Temperatur & Wetter", TileCategory.WEATHER),
    SUNSET("Sonnenuntergang", TileCategory.WEATHER),

    TRAINING_GOAL("Trainingsziel heute", TileCategory.OTHER),
    PHONE_BATTERY("Handy-Akku", TileCategory.OTHER),
    CONTROLS("Start / Pause / Stopp", TileCategory.OTHER),
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
                // Wie das Standardlayout: Karte oben, darunter die wichtigsten Zahlen.
                DashboardPage(
                    "Fahrt",
                    listOf(
                        row(RowHeight.LARGE, TileType.ROUTE),
                        row(RowHeight.MEDIUM, TileType.DISTANCE, TileType.MOVING_TIME),
                        row(RowHeight.LARGE, TileType.SPEED),
                        row(RowHeight.MEDIUM, TileType.POWER, TileType.HEART_RATE),
                        row(RowHeight.SMALL, TileType.CADENCE, TileType.ASCENT, TileType.CLOCK),
                    ),
                ),
                DashboardPage(
                    "Navi",
                    listOf(
                        row(RowHeight.MEDIUM, TileType.TURN),
                        row(RowHeight.XLARGE, TileType.ROUTE),
                        row(RowHeight.SMALL, TileType.ROUTE_REMAINING, TileType.ETA),
                        row(RowHeight.SMALL, TileType.SPEED, TileType.HEADING, TileType.CLOCK),
                    ),
                ),
                DashboardPage(
                    "Berg",
                    listOf(
                        row(RowHeight.LARGE, TileType.CLIMB),
                        row(RowHeight.LARGE, TileType.ELEVATION_PROFILE),
                        row(RowHeight.MEDIUM, TileType.GRADE, TileType.VAM),
                        row(RowHeight.MEDIUM, TileType.ASCENT, TileType.ROUTE_ASCENT_LEFT),
                        row(RowHeight.SMALL, TileType.SPEED, TileType.CADENCE, TileType.HEART_RATE),
                    ),
                ),
                DashboardPage(
                    "Leistung & Puls",
                    listOf(
                        row(RowHeight.MEDIUM, TileType.POWER, TileType.HEART_RATE),
                        row(RowHeight.MEDIUM, TileType.POWER_CHART, TileType.POWER_ZONE_CHART),
                        row(RowHeight.MEDIUM, TileType.HR_CHART, TileType.HR_ZONE_CHART),
                        row(RowHeight.MEDIUM, TileType.AVG_POWER, TileType.POWER_KG),
                        row(RowHeight.SMALL, TileType.TRAINING_GOAL, TileType.MOVING_TIME),
                    ),
                ),
                DashboardPage(
                    "Übersicht",
                    listOf(
                        row(RowHeight.MEDIUM, TileType.DISTANCE, TileType.ELAPSED_TIME),
                        row(RowHeight.MEDIUM, TileType.AVG_SPEED, TileType.MAX_SPEED),
                        row(RowHeight.MEDIUM, TileType.AVG_HR, TileType.AVG_CADENCE),
                        row(RowHeight.LARGE, TileType.WIND),
                        row(RowHeight.MEDIUM, TileType.TEMPERATURE, TileType.HEADWIND, TileType.SUNSET),
                        row(RowHeight.SMALL, TileType.CALORIES, TileType.DESCENT, TileType.PHONE_BATTERY),
                    ),
                ),
            )
        )
    }
}
