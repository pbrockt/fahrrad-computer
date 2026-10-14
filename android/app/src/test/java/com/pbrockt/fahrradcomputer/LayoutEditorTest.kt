package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardPage
import com.pbrockt.fahrradcomputer.ui.dashboard.LayoutEditor
import com.pbrockt.fahrradcomputer.ui.dashboard.RowHeight
import com.pbrockt.fahrradcomputer.ui.dashboard.TileRow
import com.pbrockt.fahrradcomputer.ui.dashboard.TileSlot
import com.pbrockt.fahrradcomputer.ui.dashboard.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEditorTest {

    private val layout = DashboardLayout(
        listOf(
            DashboardPage(
                "A",
                listOf(
                    TileRow(listOf(TileSlot(TileType.SPEED)), RowHeight.LARGE),
                    TileRow(listOf(TileSlot(TileType.HEART_RATE), TileSlot(TileType.CADENCE))),
                ),
            ),
        )
    )

    private fun row(l: DashboardLayout, r: Int) = l.pages[0].rows[r]

    @Test
    fun setTileType() {
        val l = LayoutEditor.setTileType(layout, 0, 1, 1, TileType.DISTANCE)
        assertEquals(TileType.DISTANCE, row(l, 1).tiles[1].type)
        assertEquals(TileType.HEART_RATE, row(l, 1).tiles[0].type)
    }

    @Test
    fun widenOnlyIfRowHasRoom() {
        val wide = LayoutEditor.toggleWide(layout, 0, 1, 0)
        assertTrue(row(wide, 1).tiles[0].wide)
        assertEquals(3, row(wide, 1).units)
        // Zeile voll: die zweite Kachel darf nicht auch breit werden.
        assertSame(wide, LayoutEditor.toggleWide(wide, 0, 1, 1))
        // Zurueck auf schmal geht immer.
        assertFalse(row(LayoutEditor.toggleWide(wide, 0, 1, 0), 1).tiles[0].wide)
    }

    @Test
    fun addTileUsesUnusedTypeAndRespectsLimit() {
        val l = LayoutEditor.addTile(layout, 0, 1)
        assertEquals(3, row(l, 1).tiles.size)
        val type = row(l, 1).tiles[2].type
        assertTrue(type !in setOf(TileType.SPEED, TileType.HEART_RATE, TileType.CADENCE))
        assertSame(l, LayoutEditor.addTile(l, 0, 1))
    }

    @Test
    fun removingLastTileRemovesRow() {
        val l = LayoutEditor.removeTile(layout, 0, 0, 0)
        assertEquals(1, l.pages[0].rows.size)
        assertEquals(TileType.HEART_RATE, row(l, 0).tiles[0].type)
    }

    @Test
    fun moveRowAndBounds() {
        val l = LayoutEditor.moveRow(layout, 0, 1, -1)
        assertEquals(TileType.HEART_RATE, row(l, 0).tiles[0].type)
        assertSame(layout, LayoutEditor.moveRow(layout, 0, 0, -1))
        assertSame(layout, LayoutEditor.moveRow(layout, 0, 1, 1))
    }

    @Test
    fun cycleHeightWrapsAround() {
        val l = LayoutEditor.cycleHeight(layout, 0, 0)
        assertEquals(RowHeight.XLARGE, row(l, 0).height)
        assertEquals(RowHeight.SMALL, row(LayoutEditor.cycleHeight(l, 0, 0), 0).height)
    }

    @Test
    fun pages() {
        val two = LayoutEditor.addPage(layout, "Puls")
        assertEquals(listOf("A", "Puls"), two.pages.map { it.name })
        assertEquals("B", LayoutEditor.renamePage(two, 0, "  B ").pages[0].name)
        assertSame(two, LayoutEditor.renamePage(two, 0, " "))
        assertEquals(listOf("Puls"), LayoutEditor.removePage(two, 0).pages.map { it.name })
        // Die letzte Seite bleibt.
        assertSame(layout, LayoutEditor.removePage(layout, 0))
    }

    @Test
    fun invalidIndicesChangeNothing() {
        assertSame(layout, LayoutEditor.removeTile(layout, 0, 5, 0))
        assertSame(layout, LayoutEditor.setTileType(layout, 3, 0, 0, TileType.CLOCK))
        assertSame(layout, LayoutEditor.removeRow(layout, 0, 9))
    }
}
