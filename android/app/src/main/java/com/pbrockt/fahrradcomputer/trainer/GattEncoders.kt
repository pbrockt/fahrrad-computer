package com.pbrockt.fahrradcomputer.trainer

import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Baut aus der Trittfrequenz die Kurbeldaten, die der Bluetooth-Standard verlangt: Zwift &
 * Co. wollen keine U/min, sondern "so viele Umdrehungen bis jetzt, die letzte zu Zeitpunkt
 * x" (in 1/1024 s) und rechnen die Trittfrequenz selbst daraus.
 */
class CrankSimulator {
    /** Umdrehungen gesamt, inkl. angefangener. */
    private var revolutions = 0.0
    private var lastMs = 0L
    var crankRevs: Int = 0
        private set
    /** Zeitpunkt der letzten vollen Umdrehung in 1/1024 s, laeuft nach 64 s ueber. */
    var lastEventTime: Int = 0
        private set

    fun update(nowMs: Long, cadence: Int) {
        if (lastMs == 0L || cadence <= 0) {
            lastMs = nowMs
            return
        }
        val dt = ((nowMs - lastMs) / 1000.0).coerceIn(0.0, 5.0)
        lastMs = nowMs
        val revsPerS = cadence / 60.0
        val before = floor(revolutions)
        revolutions += revsPerS * dt
        val whole = floor(revolutions)
        if (whole > before) {
            // Die letzte volle Umdrehung lag den angefangenen Rest frueher.
            val leftoverS = (revolutions - whole) / revsPerS
            val eventMs = nowMs - (leftoverS * 1000).roundToLong()
            crankRevs = (whole.toLong() and 0xFFFF).toInt()
            lastEventTime = ((eventMs * 1024 / 1000) and 0xFFFF).toInt()
        }
    }
}

object GattEncoders {
    private const val FLAG_CRANK_DATA = 0x20

    /** Cycling Power Measurement (0x2A63): Flags, Watt (sint16), Kurbel-Umdrehungen, Zeit. */
    fun powerMeasurement(watts: Int, crank: CrankSimulator?): ByteArray {
        val w = watts.coerceIn(0, Short.MAX_VALUE.toInt())
        if (crank == null) return byteArrayOf(0, 0, lo(w), hi(w))
        return byteArrayOf(
            FLAG_CRANK_DATA.toByte(), 0,
            lo(w), hi(w),
            lo(crank.crankRevs), hi(crank.crankRevs),
            lo(crank.lastEventTime), hi(crank.lastEventTime),
        )
    }

    /** Cycling Power Feature (0x2A65): nur "Kurbeldaten werden geliefert" (Bit 3). */
    val powerFeature: ByteArray = byteArrayOf(0x08, 0, 0, 0)

    /** Heart Rate Measurement (0x2A37): Flags 0 = Puls als uint8. */
    fun heartRateMeasurement(bpm: Int): ByteArray = byteArrayOf(0, bpm.coerceIn(0, 255).toByte())

    private fun lo(v: Int) = (v and 0xFF).toByte()
    private fun hi(v: Int) = ((v shr 8) and 0xFF).toByte()
}
