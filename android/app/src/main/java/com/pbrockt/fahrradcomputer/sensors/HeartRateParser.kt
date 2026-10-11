package com.pbrockt.fahrradcomputer.sensors

/**
 * Liest die Bluetooth-Charakteristik "Heart Rate Measurement" (0x2A37).
 *
 * Byte 0 sind Flags; Bit 0 sagt, ob der Puls als 1 Byte (uint8) oder 2 Byte (uint16,
 * little endian) folgt. Weitere Felder (Energie, RR-Intervalle) werden ignoriert.
 */
object HeartRateParser {

    fun parse(value: ByteArray): Int? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt() and 0xFF
        val bpm = if (flags and 0x01 == 0) {
            if (value.size < 2) return null
            value[1].toInt() and 0xFF
        } else {
            if (value.size < 3) return null
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        }
        // 0 senden manche Gurte, solange sie keinen Hautkontakt haben.
        return bpm.takeIf { it in 1..250 }
    }
}
