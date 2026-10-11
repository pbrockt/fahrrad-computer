package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout
import com.pbrockt.fahrradcomputer.ui.dashboard.LayoutCodec
import com.pbrockt.fahrradcomputer.ui.dashboard.RowHeight
import com.pbrockt.fahrradcomputer.ui.dashboard.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutCodecTest {

    @Test
    fun roundTripDefault() {
        val text = LayoutCodec.encode(DashboardLayout.DEFAULT)
        assertEquals(DashboardLayout.DEFAULT, LayoutCodec.decode(text))
    }

    @Test
    fun unknownTileTypeDropsOnlyThatTile() {
        val text = """{"version":1,"pages":[{"name":"X","rows":[
            {"height":"LARGE","tiles":[{"type":"SPEED"},{"type":"MOTOR_TEMP_GIBTS_NICHT"}]},
            {"height":"???","tiles":[{"type":"GONE"}]},
            {"height":"???","tiles":[{"type":"CLOCK","wide":true}]}
        ]}]}"""
        val l = LayoutCodec.decode(text)!!
        val rows = l.pages[0].rows
        assertEquals(2, rows.size)
        assertEquals(listOf(TileType.SPEED), rows[0].tiles.map { it.type })
        assertEquals(RowHeight.LARGE, rows[0].height)
        assertEquals(RowHeight.MEDIUM, rows[1].height)
        assertEquals(true, rows[1].tiles[0].wide)
    }

    @Test
    fun overfullRowIsCut() {
        val text = """{"pages":[{"name":"X","rows":[{"tiles":[
            {"type":"SPEED","wide":true},{"type":"CLOCK"},{"type":"DISTANCE"}]}]}]}"""
        assertEquals(2, LayoutCodec.decode(text)!!.pages[0].rows[0].tiles.size)
    }

    @Test
    fun garbageGivesNull() {
        assertNull(LayoutCodec.decode(null))
        assertNull(LayoutCodec.decode("kein json"))
        assertNull(LayoutCodec.decode("""{"pages":[]}"""))
    }
}
