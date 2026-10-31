package com.pbrockt.fahrradcomputer.ui.dashboard

import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout.Companion.MAX_ROW_UNITS

/**
 * Alle Aenderungen am Dashboard als reine Funktionen: altes Layout rein, neues raus.
 * Ungueltige Aufrufe (falscher Index, Zeile voll) geben das Layout unveraendert zurueck,
 * damit ein doppelter Tipp in der Oberflaeche nichts kaputt macht.
 */
object LayoutEditor {

    /** Neuer Inhalt - die Einstellungen der alten Kachel passen dann nicht mehr. */
    fun setTileType(l: DashboardLayout, page: Int, row: Int, col: Int, type: TileType) =
        l.mapTile(page, row, col) { if (it.type == type) it else it.copy(type = type, options = emptyMap()) }

    fun setTileOption(l: DashboardLayout, page: Int, row: Int, col: Int, key: String, value: String) =
        l.mapTile(page, row, col) { it.copy(options = it.options + (key to value)) }

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

    /** Kachel innerhalb der Zeile um [delta] Plaetze verschieben (-1 = nach links). */
    fun moveTile(l: DashboardLayout, page: Int, row: Int, col: Int, delta: Int): DashboardLayout {
        val r = l.rowAt(page, row) ?: return l
        val target = col + delta
        if (col !in r.tiles.indices || target !in r.tiles.indices) return l
        val tiles = r.tiles.toMutableList()
        tiles.add(target, tiles.removeAt(col))
        return l.mapRow(page, row) { it.copy(tiles = tiles) }
    }

    /** Passt die Kachel noch in die Zeile [target]? */
    fun canMoveToRow(l: DashboardLayout, page: Int, row: Int, col: Int, target: Int): Boolean {
        val tile = l.rowAt(page, row)?.tiles?.getOrNull(col) ?: return false
        val t = l.rowAt(page, target) ?: return false
        return target != row && t.units + tile.units <= MAX_ROW_UNITS
    }

    /**
     * Kachel ans Ende einer anderen Zeile haengen. Wird ihre alte Zeile dadurch leer,
     * verschwindet sie. Gibt das Layout und die neue Stelle (Zeile, Spalte) zurueck.
     */
    fun moveTileToRow(l: DashboardLayout, page: Int, row: Int, col: Int, target: Int): Pair<DashboardLayout, Pair<Int, Int>> {
        if (!canMoveToRow(l, page, row, col, target)) return l to (row to col)
        val p = l.pages[page]
        val tile = p.rows[row].tiles[col]
        val rows = p.rows.toMutableList()
        rows[target] = rows[target].copy(tiles = rows[target].tiles + tile)
        val newCol = rows[target].tiles.lastIndex
        val rest = rows[row].tiles.filterIndexed { i, _ -> i != col }
        var newRow = target
        if (rest.isEmpty()) {
            rows.removeAt(row)
            if (target > row) newRow--
        } else {
            rows[row] = rows[row].copy(tiles = rest)
        }
        return l.mapPage(page) { it.copy(rows = rows) } to (newRow to newCol)
    }

    /** Kachel in eine eigene, neue Zeile direkt darunter. */
    fun moveTileToNewRow(l: DashboardLayout, page: Int, row: Int, col: Int): DashboardLayout {
        val r = l.rowAt(page, row) ?: return l
        if (col !in r.tiles.indices || r.tiles.size < 2) return l
        val tile = r.tiles[col]
        return l.mapPage(page) { p ->
            val rows = p.rows.toMutableList()
            rows[row] = r.copy(tiles = r.tiles.filterIndexed { i, _ -> i != col })
            rows.add(row + 1, TileRow(listOf(tile), r.height))
            p.copy(rows = rows)
        }
    }

    /** Seite um [delta] Plaetze verschieben (-1 = nach links). */
    fun movePage(l: DashboardLayout, page: Int, delta: Int): DashboardLayout {
        val target = page + delta
        if (page !in l.pages.indices || target !in l.pages.indices) return l
        val pages = l.pages.toMutableList()
        pages.add(target, pages.removeAt(page))
        return l.copy(pages = pages)
    }

    /** Neue Seite als Kopie einer Vorlage; gleicher Name bekommt eine Nummer. */
    fun addPageFrom(l: DashboardLayout, template: DashboardPage): DashboardLayout {
        val names = l.pages.map { it.name }.toSet()
        var name = template.name
        var n = 2
        while (name in names) name = "${template.name} ${n++}"
        return l.copy(pages = l.pages + template.copy(name = name))
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
