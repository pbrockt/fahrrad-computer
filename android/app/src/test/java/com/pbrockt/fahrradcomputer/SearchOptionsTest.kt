package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.routes.PlaceSearch
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardPage
import com.pbrockt.fahrradcomputer.ui.dashboard.LayoutCodec
import com.pbrockt.fahrradcomputer.ui.dashboard.LayoutEditor
import com.pbrockt.fahrradcomputer.ui.dashboard.TileOptions
import com.pbrockt.fahrradcomputer.ui.dashboard.TileRow
import com.pbrockt.fahrradcomputer.ui.dashboard.TileSlot
import com.pbrockt.fahrradcomputer.ui.dashboard.TileType
import com.pbrockt.fahrradcomputer.ui.dashboard.opt
import com.pbrockt.fahrradcomputer.weather.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOptionsTest {

    @Test
    fun parsePhoton() {
        // Gekuerzte Antwort im Format von photon.komoot.io (ausgedachte Orte).
        val json = """{"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"osm_key":"amenity","osm_value":"cafe","name":"Café Test","street":"Marktstraße",
             "housenumber":"11","city":"Koblenz","postcode":"56068","country":"Deutschland"},
             "geometry":{"type":"Point","coordinates":[7.5945,50.3610]}},
            {"type":"Feature","properties":{"osm_value":"house","street":"Hauptstraße","housenumber":"3","city":"Lahnstein"},
             "geometry":{"type":"Point","coordinates":[7.61,50.30]}}]}"""
        val p = PlaceSearch.parse(json, 50.35, 7.6)
        assertEquals(2, p.size)
        assertEquals("Café Test", p[0].name)
        assertEquals("Marktstraße 11, 56068 Koblenz", p[0].address)
        assertEquals("cafe", p[0].kind)
        assertEquals(1230.0, p[0].distanceM!!, 60.0)
        // Ohne Namen: Adresse als Name, nicht doppelt in der Adresszeile
        assertEquals("Hauptstraße 3", p[1].name)
        assertEquals("Lahnstein", p[1].address)
    }

    @Test
    fun tileOptionsDefaultAndStored() {
        val slot = TileSlot(TileType.WIND)
        assertEquals("rel", slot.options.opt(TileOptions.WIND_DIRECTION))
        val layout = DashboardLayout(listOf(DashboardPage("A", listOf(TileRow(listOf(slot))))))
        val changed = LayoutEditor.setTileOption(layout, 0, 0, 0, "dir", "abs")
        val back = LayoutCodec.decode(LayoutCodec.encode(changed))!!
        assertEquals("abs", back.pages[0].rows[0].tiles[0].options.opt(TileOptions.WIND_DIRECTION))
        // Neuer Kacheltyp: alte Einstellungen weg
        val retyped = LayoutEditor.setTileType(changed, 0, 0, 0, TileType.HEADING)
        assertTrue(retyped.pages[0].rows[0].tiles[0].options.isEmpty())
        // Unbekannter Wert -> Standard
        assertEquals("rel", mapOf("dir" to "quer").opt(TileOptions.WIND_DIRECTION))
    }

    @Test
    fun beaufort() {
        assertEquals(0, Wind.beaufort(0.5))
        assertEquals(3, Wind.beaufort(15.0))
        assertEquals(6, Wind.beaufort(45.0))
        assertEquals(12, Wind.beaufort(130.0))
    }
}
