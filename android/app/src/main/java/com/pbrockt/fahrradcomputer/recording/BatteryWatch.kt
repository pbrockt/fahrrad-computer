package com.pbrockt.fahrradcomputer.recording

/**
 * Merkt sich, welche Akku-Warnungen in dieser Fahrt schon kamen: Handy bei 20 % und 10 %,
 * jeder Sensor einmal unter 15 %. So piept es nicht bei jedem Messwert erneut.
 */
class BatteryWatch {
    private val phoneDone = mutableSetOf<Int>()
    private val sensorsDone = mutableSetOf<String>()

    /** Neue Warnungen fuer [phone] (Prozent) und [sensors] (Name -> Prozent). */
    fun check(phone: Int?, sensors: Map<String, Int?>): List<String> = buildList {
        if (phone != null) {
            // Nur die niedrigste erreichte Stufe melden - wer mit 9 % startet, bekommt eine Warnung.
            val hit = PHONE_LEVELS.filter { phone <= it && it !in phoneDone }
            if (hit.isNotEmpty()) {
                phoneDone += hit
                add("Handy-Akku nur noch $phone %")
            }
        }
        for ((name, level) in sensors) {
            if (level != null && level < SENSOR_LEVEL && sensorsDone.add(name)) add("$name: Akku $level %")
        }
    }

    companion object {
        val PHONE_LEVELS = listOf(20, 10)
        const val SENSOR_LEVEL = 15
    }
}
