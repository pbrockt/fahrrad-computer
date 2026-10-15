package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.ui.BellSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BellTest {

    private val s = BellSynth.ring()
    private fun peak(fromS: Double, toS: Double) =
        s.slice((fromS * BellSynth.SAMPLE_RATE).toInt() until (toS * BellSynth.SAMPLE_RATE).toInt()).maxOf { abs(it.toInt()) }

    @Test
    fun lengthAndNoClipping() {
        assertEquals(BellSynth.SAMPLE_RATE, s.size) // 1 Sekunde
        val max = s.maxOf { abs(it.toInt()) }
        assertTrue("laut genug: $max", max > 25_000)
        assertTrue("nicht uebersteuert: $max", max < Short.MAX_VALUE)
    }

    @Test
    fun twoStrikesThenFadeOut() {
        val beforeSecond = peak(0.12, 0.165)
        val second = peak(0.17, 0.2)
        assertTrue("zweiter Anschlag hoerbar: $second > $beforeSecond", second > beforeSecond * 1.3)
        assertTrue("klingt aus", peak(0.9, 1.0) < second / 4)
    }

    @Test
    fun noClickAtStart() {
        assertTrue(abs(s[0].toInt()) < 100)
    }
}
