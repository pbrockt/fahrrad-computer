package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.BatteryWatch
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryWatchTest {

    @Test
    fun phoneWarnsOncePerLevel() {
        val w = BatteryWatch()
        assertEquals(emptyList<String>(), w.check(45, emptyMap()))
        assertEquals(listOf("Handy-Akku nur noch 20 %"), w.check(20, emptyMap()))
        assertEquals(emptyList<String>(), w.check(19, emptyMap()))
        assertEquals(listOf("Handy-Akku nur noch 10 %"), w.check(10, emptyMap()))
        assertEquals(emptyList<String>(), w.check(5, emptyMap()))
    }

    @Test
    fun startingLowGivesOneWarning() {
        val w = BatteryWatch()
        assertEquals(1, w.check(8, emptyMap()).size)
        assertEquals(0, w.check(7, emptyMap()).size)
    }

    @Test
    fun sensorsWarnOnceEach() {
        val w = BatteryWatch()
        assertEquals(listOf("Puls: Akku 12 %"), w.check(null, mapOf("Puls" to 12, "Trittfrequenz" to 60)))
        assertEquals(listOf("Trittfrequenz: Akku 9 %"), w.check(null, mapOf("Puls" to 11, "Trittfrequenz" to 9)))
        assertEquals(emptyList<String>(), w.check(null, mapOf("Puls" to 5, "Trittfrequenz" to null)))
    }
}
