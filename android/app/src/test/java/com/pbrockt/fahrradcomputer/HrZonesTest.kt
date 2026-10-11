package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.data.HrZones
import org.junit.Assert.assertEquals
import org.junit.Test

class HrZonesTest {

    private val zones = HrZones(200)

    @Test
    fun thresholds() = assertEquals(listOf(120, 140, 160, 180), zones.thresholds)

    @Test
    fun zoneBoundaries() {
        assertEquals(1, zones.zoneOf(90))
        assertEquals(1, zones.zoneOf(119))
        assertEquals(2, zones.zoneOf(120))
        assertEquals(3, zones.zoneOf(150))
        assertEquals(4, zones.zoneOf(179))
        assertEquals(5, zones.zoneOf(180))
        assertEquals(5, zones.zoneOf(210))
    }
}
