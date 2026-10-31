package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.routes.RouteProfile
import com.pbrockt.fahrradcomputer.routes.RouteSections
import com.pbrockt.fahrradcomputer.routes.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSectionsTest {

    /** Ausgedachte Strecke: 1 km flach, 2 km rauf (erst 3 %, dann 9 %), 1 km runter, 2 km flach. */
    private val profile = RouteProfile.resample(
        doubleArrayOf(0.0, 1000.0, 2000.0, 3000.0, 4000.0, 6000.0),
        doubleArrayOf(100.0, 100.0, 130.0, 220.0, 150.0, 150.0),
    )!!

    @Test
    fun splitsIntoTrends() {
        val s = profile.sections
        assertEquals(listOf(Trend.FLAT, Trend.UP, Trend.DOWN, Trend.FLAT), s.map { it.trend })
        assertEquals(1000.0, s[1].startM, 60.0)
        assertEquals(3000.0, s[1].endM, 60.0)
        assertEquals(1, s[1].climb?.number)
        // lueckenlos
        for (i in 1 until s.size) assertEquals(s[i - 1].endM, s[i].startM, 0.01)
        assertEquals(profile.lengthM, s.last().endM, 0.01)
    }

    @Test
    fun climbShowsItsSteps() {
        val ramps = profile.sections[1].ramps
        assertTrue(ramps.size in 2..RouteSections.MAX_RAMPS)
        assertEquals(3.0, ramps.first().grade, 1.0)
        assertEquals(9.0, ramps.last().grade, 1.0)
    }

    @Test
    fun neverMoreThanTenRamps() {
        // Saegezahn: jede 200 m eine andere Steigung
        val d = DoubleArray(60) { it * 200.0 }
        val e = DoubleArray(60) { i -> 100.0 + i * 12 + if (i % 2 == 0) 0.0 else 10.0 }
        val p = RouteProfile.resample(d, e)!!
        val ramps = RouteSections.ramps(p, 0.0, p.lengthM)
        assertTrue(ramps.size <= RouteSections.MAX_RAMPS)
        assertEquals(p.lengthM, ramps.sumOf { it.lengthM }, 0.01)
    }

    @Test
    fun indexAtPosition() {
        assertEquals(1, profile.sectionIndexAt(2000.0))
        assertEquals(3, profile.sectionIndexAt(5900.0))
        assertEquals(-1, profile.sectionIndexAt(7000.0))
    }
}
