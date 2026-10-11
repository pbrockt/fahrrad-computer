package com.pbrockt.fahrradcomputer.ui.dashboard

/** Was eine Kachel anzeigen kann. */
enum class TileType(val label: String) {
    SPEED("Geschwindigkeit"),
    HEART_RATE("Puls"),
    CADENCE("Trittfrequenz"),
    DISTANCE("Distanz"),
    MOVING_TIME("Fahrzeit"),
    AVG_SPEED("Ø Geschwindigkeit"),
    MAX_SPEED("Max. Geschwindigkeit"),
    AVG_HR("Ø Puls"),
    ALTITUDE("Höhe"),
    CLOCK("Uhrzeit"),
    ROUTE("Strecke"),
}

/** Eine Kachel in einer Zeile; [weight] ist ihr Anteil an der Zeilenbreite. */
data class TileSlot(val type: TileType, val weight: Float = 1f)

/** Eine Zeile des Dashboards; [height] ist ihr Anteil an der Bildschirmhoehe. */
data class TileRow(val tiles: List<TileSlot>, val height: Float = 1f)

/**
 * Das Dashboard als Daten statt als fester Code. In Phase 2 kommt der Editor dazu, der
 * genau diese Struktur veraendert und als JSON speichert - die Anzeige bleibt dieselbe.
 */
data class DashboardLayout(val rows: List<TileRow>) {

    companion object {
        val DEFAULT = DashboardLayout(
            listOf(
                TileRow(listOf(TileSlot(TileType.SPEED)), height = 1.6f),
                TileRow(listOf(TileSlot(TileType.HEART_RATE), TileSlot(TileType.CADENCE)), height = 1.2f),
                TileRow(listOf(TileSlot(TileType.DISTANCE), TileSlot(TileType.MOVING_TIME))),
                TileRow(listOf(TileSlot(TileType.AVG_SPEED), TileSlot(TileType.CLOCK)), height = 0.8f),
                // Platzhalter fuer die Navigation: bis zur Karte (Phase 6) die Streckenskizze.
                TileRow(listOf(TileSlot(TileType.ROUTE)), height = 1.6f),
            )
        )
    }
}
