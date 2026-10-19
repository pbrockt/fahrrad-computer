package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.trainer.CrankSimulator
import com.pbrockt.fahrradcomputer.trainer.GattEncoders
import com.pbrockt.fahrradcomputer.trainer.TrainerProfile
import com.pbrockt.fahrradcomputer.trainer.VirtualPower
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualPowerTest {

    private val profile = TrainerProfile(
        body = BodyProfile(weightKg = 80.0, age = 35, sex = Sex.MALE),
        maxHr = 180,
        restHr = 60,
        ftpWatts = 200,
    )

    /** Laesst das Modell [seconds] Sekunden gleichmaessig laufen, gibt die letzten Watt zurueck. */
    private fun VirtualPower.run(seconds: Int, hr: Int?, cad: Int?, level: Int = 5, startS: Int = 0, p: TrainerProfile = profile): Int {
        var w = 0
        for (i in 1..seconds) w = update((startS + i) * 1000L, hr, cad, level, p)
        return w
    }

    @Test
    fun ftpEstimateFromBody() {
        assertEquals(208.0, VirtualPower.estimateFtp(BodyProfile(80.0, 35, Sex.MALE)), 0.01)
        // 20 Jahre aelter: 10 % weniger
        assertEquals(187.2, VirtualPower.estimateFtp(BodyProfile(80.0, 55, Sex.MALE)), 0.01)
        assertEquals(165.0, VirtualPower.estimateFtp(BodyProfile(75.0, 30, Sex.FEMALE)), 0.01)
    }

    @Test
    fun hrPowerOverHeartRateReserve() {
        // 85 % der Reserve (60 + 0,85*120 = 162) = FTP
        assertEquals(200.0, VirtualPower.hrPower(162, profile), 0.01)
        assertEquals(0.0, VirtualPower.hrPower(70, profile), 0.01)
        // gedeckelt bei 150 % FTP
        assertEquals(300.0, VirtualPower.hrPower(250, profile), 0.01)
    }

    @Test
    fun noPedalingIsZeroWatts() {
        val m = VirtualPower()
        m.run(30, hr = 150, cad = 90)
        assertEquals(0, m.update(31_000, 150, 0, 5, profile))
    }

    @Test
    fun cadenceDefaultBeforeLearning() {
        // Ohne Puls: 90 U/min auf Stufe 5 = 60 % FTP
        assertEquals(120, VirtualPower().run(20, hr = null, cad = 90))
    }

    @Test
    fun levelAndCadenceActImmediately() {
        val m = VirtualPower()
        val base = m.run(20, hr = null, cad = 90, level = 5)
        val harder = m.run(10, hr = null, cad = 90, level = 10, startS = 20)
        assertEquals(2.0, harder.toDouble() / base, 0.05)
    }

    @Test
    fun learnsLevelFromHeartRate() {
        // Puls 132 = 60 % Reserve -> (0,60-0,15)/0,70 * 200 = ~129 W. Startschaetzung waere 120 W.
        val m = VirtualPower()
        val w = m.run(1200, hr = 132, cad = 90)
        assertEquals(129.0, w.toDouble(), 3.0)
        assertEquals(129.0, m.gain, 3.0)
    }

    @Test
    fun heartRateOnlyWithoutCadenceSensor() {
        assertEquals(129.0, VirtualPower().run(120, hr = 132, cad = null).toDouble(), 2.0)
    }

    @Test
    fun calibrationScalesOutput() {
        val w = VirtualPower().run(20, hr = null, cad = 90, p = profile.copy(calibrationPercent = 110))
        assertEquals(132, w)
    }

    @Test
    fun crankSimulatorCountsRevolutions() {
        val c = CrankSimulator()
        c.update(1_000, 60)
        for (s in 2..11) c.update(s * 1000L, 60)
        // 10 s bei 60 U/min = 10 Umdrehungen, letzte genau bei 11 s
        assertEquals(10, c.crankRevs)
        assertEquals((11_000L * 1024 / 1000 and 0xFFFF).toInt(), c.lastEventTime)
    }

    @Test
    fun crankSimulatorStopsWhenNotPedaling() {
        val c = CrankSimulator()
        c.update(1_000, 90)
        c.update(2_000, 90)
        val revs = c.crankRevs
        c.update(3_000, 0)
        c.update(4_000, 0)
        assertEquals(revs, c.crankRevs)
    }

    @Test
    fun powerMeasurementBytes() {
        val c = CrankSimulator()
        c.update(1_000, 60)
        c.update(2_000, 60)
        val v = GattEncoders.powerMeasurement(300, c)
        val t = c.lastEventTime
        assertArrayEquals(
            byteArrayOf(0x20, 0, 0x2C, 0x01, 1, 0, (t and 0xFF).toByte(), (t shr 8).toByte()),
            v,
        )
        assertArrayEquals(byteArrayOf(0, 0, 0x2C, 0x01), GattEncoders.powerMeasurement(300, null))
    }

    @Test
    fun encodedPowerIsReadableByOwnParser() {
        val c = CrankSimulator()
        c.update(1_000, 90)
        c.update(2_000, 90)
        val m = com.pbrockt.fahrradcomputer.sensors.CyclingParsers.parsePower(GattEncoders.powerMeasurement(250, c))!!
        assertEquals(250, m.watts)
        assertTrue(m.crank != null)
    }

    @Test
    fun heartRateBytes() {
        assertArrayEquals(byteArrayOf(0, 142.toByte()), GattEncoders.heartRateMeasurement(142))
    }
}
