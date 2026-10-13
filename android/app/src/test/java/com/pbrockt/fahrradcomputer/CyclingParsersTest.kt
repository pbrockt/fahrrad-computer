package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.sensors.CadenceCalculator
import com.pbrockt.fahrradcomputer.sensors.CrankData
import com.pbrockt.fahrradcomputer.sensors.CyclingParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CyclingParsersTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun cscCrankOnly() {
        // Flags 0x02, Kurbel 0x0102 = 258 Umdrehungen, Zeit 0x0400 = 1024 (= 1 s)
        assertEquals(CrankData(258, 1024), CyclingParsers.parseCscCrank(bytes(0x02, 0x02, 0x01, 0x00, 0x04)))
    }

    @Test
    fun cscCrankBehindWheelData() {
        val v = bytes(0x03, 1, 0, 0, 0, 0, 0, /* Kurbel */ 10, 0, 0x00, 0x08)
        assertEquals(CrankData(10, 2048), CyclingParsers.parseCscCrank(v))
    }

    @Test
    fun cscWheelOnlyHasNoCadence() {
        assertNull(CyclingParsers.parseCscCrank(bytes(0x01, 1, 0, 0, 0, 0, 0)))
    }

    @Test
    fun powerSimple() {
        val m = CyclingParsers.parsePower(bytes(0x00, 0x00, 0xC8, 0x00))!!
        assertEquals(200, m.watts)
        assertNull(m.crank)
    }

    @Test
    fun powerWithCrankAfterBalanceTorqueAndWheel() {
        // Flags: Bit 0 (Balance, 1 B), Bit 2 (Drehmoment, 2 B), Bit 4 (Rad, 6 B), Bit 5 (Kurbel)
        val flags = 0x01 or 0x04 or 0x10 or 0x20
        val v = bytes(
            flags, 0x00, 0x2C, 0x01, // 300 W
            50, // Balance
            0, 0, // Drehmoment
            0, 0, 0, 0, 0, 0, // Rad
            7, 0, 0x00, 0x04, // Kurbel: 7 U, Zeit 1024
        )
        val m = CyclingParsers.parsePower(v)!!
        assertEquals(300, m.watts)
        assertEquals(CrankData(7, 1024), m.crank)
    }

    @Test
    fun negativePowerIsZero() {
        assertEquals(0, CyclingParsers.parsePower(bytes(0x00, 0x00, 0xF6, 0xFF))!!.watts)
    }

    @Test
    fun cadenceFromCounters() {
        val c = CadenceCalculator()
        assertEquals(0, c.update(CrankData(100, 0), 0))
        // 2 Umdrehungen in 1,5 s = 80 U/min
        assertEquals(80, c.update(CrankData(102, 1536), 1_500))
    }

    @Test
    fun cadenceHandlesCounterOverflow() {
        val c = CadenceCalculator()
        c.update(CrankData(65535, 65000), 0)
        // 1 Umdrehung, Zeit laeuft ueber: (512 - 65000) mod 65536 = 1048 -> ~58,6 U/min
        assertEquals(59, c.update(CrankData(0, 512), 1_000))
    }

    @Test
    fun cadenceDropsToZeroWhenCoasting() {
        val c = CadenceCalculator()
        c.update(CrankData(1, 0), 0)
        assertEquals(60, c.update(CrankData(2, 1024), 1_000))
        // Gleicher Zaehlerstand -> erst halten, nach 3 s ohne Umdrehung 0
        assertEquals(60, c.update(CrankData(2, 1024), 2_000))
        assertEquals(0, c.update(CrankData(2, 1024), 4_500))
    }
}
