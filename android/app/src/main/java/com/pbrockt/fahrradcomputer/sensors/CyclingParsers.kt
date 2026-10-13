package com.pbrockt.fahrradcomputer.sensors

import kotlin.math.roundToInt

/** Kurbel-Umdrehungen: Zaehlerstand und Zeitpunkt der letzten Umdrehung in 1/1024 s. */
data class CrankData(val revolutions: Int, val eventTime: Int)

data class PowerMeasurement(val watts: Int, val crank: CrankData?)

/**
 * Liest die Bluetooth-Standardprofile fuers Rad. Alle Werte sind little endian.
 *
 * - CSC Measurement (0x2A5B): Flags (1 Byte); Bit 0 = Raddaten (uint32 Umdrehungen +
 *   uint16 Zeit) folgen, Bit 1 = Kurbeldaten (uint16 Umdrehungen + uint16 Zeit) folgen.
 * - Cycling Power Measurement (0x2A63): Flags (2 Byte), Leistung als sint16 in Watt,
 *   danach optionale Felder in fester Reihenfolge - die Kurbeldaten (Bit 5) stehen erst
 *   hinter Pedal-Balance (Bit 0), Drehmoment (Bit 2) und Raddaten (Bit 4).
 */
object CyclingParsers {

    fun parseCscCrank(v: ByteArray): CrankData? {
        if (v.isEmpty()) return null
        val flags = u8(v, 0)
        var offset = 1
        if (flags and 0x01 != 0) offset += 6
        if (flags and 0x02 == 0 || v.size < offset + 4) return null
        return CrankData(u16(v, offset), u16(v, offset + 2))
    }

    fun parsePower(v: ByteArray): PowerMeasurement? {
        if (v.size < 4) return null
        val flags = u16(v, 0)
        val watts = (u16(v, 2).toShort()).toInt()
        var offset = 4
        if (flags and 0x01 != 0) offset += 1
        if (flags and 0x04 != 0) offset += 2
        if (flags and 0x10 != 0) offset += 6
        val crank = if (flags and 0x20 != 0 && v.size >= offset + 4) {
            CrankData(u16(v, offset), u16(v, offset + 2))
        } else null
        return PowerMeasurement(watts.coerceAtLeast(0), crank)
    }

    private fun u8(v: ByteArray, i: Int) = v[i].toInt() and 0xFF
    private fun u16(v: ByteArray, i: Int) = u8(v, i) or (u8(v, i + 1) shl 8)
}

/**
 * Macht aus den Zaehlerstaenden der Kurbel eine Trittfrequenz.
 *
 * Sensoren melden nicht "U/min", sondern "bis jetzt N Umdrehungen, die letzte zur Zeit T".
 * Die Trittfrequenz ergibt sich aus der Differenz zweier Meldungen. Beide Zaehler sind
 * 16 Bit und laufen ueber - deshalb die Differenz modulo 65536. Bleibt der Zaehler
 * stehen (Rollen lassen), meldet der Sensor weiter den alten Stand; nach [STOP_MS] ohne
 * neue Umdrehung gilt dann 0.
 */
class CadenceCalculator {

    private var lastRevs: Int? = null
    private var lastTime = 0
    private var lastChangeMs = 0L
    private var rpm = 0

    fun update(data: CrankData, nowMs: Long): Int {
        val prev = lastRevs
        if (prev == null) {
            lastRevs = data.revolutions
            lastTime = data.eventTime
            lastChangeMs = nowMs
            return 0
        }
        val dRevs = (data.revolutions - prev) and 0xFFFF
        val dTime = (data.eventTime - lastTime) and 0xFFFF
        if (dRevs > 0 && dTime > 0) {
            rpm = (dRevs * 60.0 * 1024 / dTime).roundToInt().coerceAtMost(MAX_RPM)
            lastRevs = data.revolutions
            lastTime = data.eventTime
            lastChangeMs = nowMs
        } else if (nowMs - lastChangeMs > STOP_MS) {
            rpm = 0
        }
        return rpm
    }

    fun reset() {
        lastRevs = null
        rpm = 0
    }

    companion object {
        const val STOP_MS = 3_000L
        const val MAX_RPM = 220
    }
}
