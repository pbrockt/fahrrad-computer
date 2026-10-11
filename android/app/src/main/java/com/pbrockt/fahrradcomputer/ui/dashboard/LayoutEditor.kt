package com.pbrockt.fahrradcomputer.ui.dashboard

import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout.Companion.MAX_ROW_UNITS

/**
 * Alle Aenderungen am Dashboard als reine Funktionen: altes Layout rein, neues raus.
 * Ungueltige Aufrufe (falscher Index, Zeile voll) geben das Layout unveraendert zurueck,
 * damit ein doppelter Tipp in der Oberflaeche nichts kaputt macht.
 */
object LayoutEditor {

    fun setTileType(l: DashboardLayout, page: Int, row: Int, col: Int, type: TileType) =
        l.mapTile(page, row, col) { it.copy(type = type) }

    /** Schmal <-> breit, sofern die Zeile dafuer Platz hat. */
    fun toggleWide(l: DashboardLayout, page: Int, row: Int, col: Int): DashboardLayout {
        val r = l.rowAt(page, row) ?: return l
        if (!canWiden(r, col)) return l
        return l.mapTile(page, row, col) { it.copy(wide = !it.wide) }
    }

    /** Darf die Kachel breit werden (oder ist sie es schon und darf zurueck)? */
    fun canWiden(row: TileRow, col: Int): Boolean {
        val tile = row.tiles.getOrNull(col) ?: return false
        return tile.wide || row.units < MAX_ROW_UNITS
    }

    /** Entfernt eine Kachel; eine dadurch leere Zeile verschwindet mit. */
    fun removeTile(l: DashboardLayout, page: Int, row: Int, col: Int): DashboardLayout {
        val r = l.rowAt(page, row) ?: return l
        if (col !in r.tiles.indices) return l
        val tiles = r.tiles.filterIndexed { i, _ -> i != col }
        return l.mapPage(page) { p ->
            p.copy(rows = if (tiles.isEmpty()) p.rows.without(row) else p.rows.replace(row, r.copy(tiles = tiles)))
        }
    }

    fun canAddTile(l: DashboardLayout, page: Int, row: Int): Boolean =
        (l.rowAt(page, row)?.units ?: MAX_ROW_UNITS) < MAX_ROW_UNITS

    /** Haengt eine Kachel an - mit einem Wert, der auf der Seite noch fehlt. */
    fun addTile(l: DashboardLayout, page: Int, row: Int): DashboardLayout {
        if (!canAddTile(l, page, row)) return l
        val type = unusedType(l.pages[page])
        return l.mapRow(page, row) { it.copy(tiles = it.tiles + TileSlot(type)) }
    }

    fun addRow(l: DashboardLayout, page: Int): DashboardLayout {
        val p = l.pages.getOrNull(page) ?: return l
        return l.mapPage(page) { it.copy(rows = it.rows + TileRow(listOf(TileSlot(unusedType(p))))) }
    }

    fun removeRow(l: DashboardLayout, page: Int, row: Int): DashboardLayout {
        if (l.rowAt(page, row) == null) return l
        return l.mapPage(page) { it.copy(rows = it.rows.without(row)) }
    }

    fun cycleHeight(l: DashboardLayout, page: Int, row: Int) =
        l.mapRow(page, row) { it.copy(height = it.height.next()) }

    /** Verschiebt eine Zeile um [delta] Plaetze (-1 = nach oben). */
    fun moveRow(l: DashboardLayout, page: Int, row: Int, delta: Int): DashboardLayout {
        val p = l.pages.getOrNull(page) ?: return l
        val target = row + delta
        if (row !in p.rows.indices || target !in p.rows.indices) return l
        val rows = p.rows.toMutableList()
        rows.add(target, rows.removeAt(row))
        return l.mapPage(page) { it.copy(rows = rows) }
    }

    fun addPage(l: DashboardLayout, name: String): DashboardLayout =
        l.copy(pages = l.pages + DashboardPage(name.ifBlank { "Seite ${l.pages.size + 1}" }, listOf(
            TileRow(listOf(TileSlot(TileType.SPEED)), RowHeight.LARGE),
        )))

    /** Die letzte Seite bleibt immer stehen - ohne Seite gaebe es kein Dashboard. */
    fun removePage(l: DashboardLayout, page: Int): DashboardLayout {
        if (l.pages.size <= 1 || page !in l.pages.indices) return l
        return l.copy(pages = l.pages.without(page))
    }

    fun renamePage(l: DashboardLayout, page: Int, name: String): DashboardLayout {
        if (name.isBlank()) return l
        return l.mapPage(page) { it.copy(name = name.trim()) }
    }

    private fun unusedType(p: DashboardPage): TileType {
        val used = p.rows.flatMap { r -> r.tiles.map { it.type } }.toSet()
        return TileType.entries.firstOrNull { it !in used } ?: TileType.SPEED
    }

    // --- Hilfen ---

    private fun DashboardLayout.rowAt(page: Int, row: Int): TileRow? = pages.getOrNull(page)?.rows?.getOrNull(row)

    private fun DashboardLayout.mapPage(page: Int, f: (DashboardPage) -> DashboardPage): DashboardLayout {
        if (page !in pages.indices) return this
        return copy(pages = pages.replace(page, f(pages[page])))
    }

    private fun DashboardLayout.mapRow(page: Int, row: Int, f: (TileRow) -> TileRow): DashboardLayout {
        val r = rowAt(page, row) ?: return this
        return mapPage(page) { it.copy(rows = it.rows.replace(row, f(r))) }
    }

    private fun DashboardLayout.mapTile(page: Int, row: Int, col: Int, f: (TileSlot) -> TileSlot): DashboardLayout {
        val t = rowAt(page, row)?.tiles?.getOrNull(col) ?: return this
        return mapRow(page, row) { it.copy(tiles = it.tiles.replace(col, f(t))) }
    }

    private fun <T> List<T>.replace(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }
    private fun <T> List<T>.without(i: Int): List<T> = filterIndexed { j, _ -> j != i }
}
