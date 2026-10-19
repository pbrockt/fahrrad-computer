package com.pbrockt.fahrradcomputer.trainer

import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.Sex
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Was die Watt-Schaetzung ueber Fahrer und Heimtrainer wissen muss. */
data class TrainerProfile(
    val body: BodyProfile = BodyProfile(),
    val maxHr: Int = 180,
    val restHr: Int = 60,
    /** Eigene Dauerleistung (FTP) in Watt; 0 = aus Gewicht, Alter und Geschlecht schaetzen. */
    val ftpWatts: Int = 0,
    /** Korrektur in Prozent, falls Zwift sich dauerhaft zu leicht oder zu schwer anfuehlt. */
    val calibrationPercent: Int = 100,
) {
    val ftp: Double get() = if (ftpWatts > 0) ftpWatts.toDouble() else VirtualPower.estimateFtp(body)
}

/**
 * Virtuelle Watt fuer einen Heimtrainer ohne Leistungsmesser - aus Puls und Trittfrequenz.
 *
 * Der Puls allein ist zu traege: nach einem Antritt dauert es 10-30 s, bis er steigt. Die
 * Trittfrequenz dagegen reagiert sofort, sagt aber nichts ueber den Widerstand. Deshalb:
 *
 * 1. **Trittfrequenz-Anteil** (schnell): `gain · Stufe/5 · (U/min / 90)^1,7`. Die Potenz liegt
 *    zwischen Reibungsbremse (~linear) und Magnetbremse (~quadratisch).
 * 2. **Puls-Anteil** (langsam, aber absolut): Anteil an der Herzfrequenzreserve, auf die FTP
 *    abgebildet - 15 % Reserve = 0 W, 85 % Reserve = FTP.
 * 3. **Lernen**: `gain` wird langsam so nachgezogen, dass der Trittfrequenz-Anteil im Mittel
 *    zum Puls-Anteil passt. Damit der Pulsverzug nicht stoert, wird die Trittfrequenz vorher
 *    genauso traege geglaettet. So stimmt das Niveau nach ein paar Minuten, und Antritte
 *    oder ein Stufenwechsel wirken trotzdem sofort.
 *
 * Ohne Trittfrequenz-Sensor gibt es nur den Puls-Anteil, ohne Pulsgurt nur den (ungelernten)
 * Trittfrequenz-Anteil. Wer nicht tritt, leistet 0 W.
 */
class VirtualPower(initialGain: Double = 0.0) {

    /** Watt bei 90 U/min auf Stufe 5 - das, was gelernt wird. 0 = noch nicht bekannt. */
    var gain: Double = initialGain
        private set

    private var lastMs = 0L
    private var cadSlow: Double? = null
    private var hrPowerSlow: Double? = null
    private var output: Double? = null

    fun reset() {
        lastMs = 0L
        cadSlow = null
        hrPowerSlow = null
        output = null
    }

    /**
     * Ein Messschritt, normalerweise einmal pro Sekunde. [heartRate] und [cadence] sind null,
     * wenn der Sensor fehlt oder sein Wert veraltet ist. [level] ist die Widerstandsstufe 1-10.
     */
    fun update(nowMs: Long, heartRate: Int?, cadence: Int?, level: Int, profile: TrainerProfile): Int {
        val dt = if (lastMs == 0L) 1.0 else ((nowMs - lastMs) / 1000.0).coerceIn(0.1, 5.0)
        lastMs = nowMs
        val ftp = profile.ftp
        if (gain <= 0) gain = ftp * DEFAULT_GAIN_OF_FTP

        val hrPower = heartRate?.let { hrPower(it, profile) }
        val raw = cadence?.let { cadenceShape(it) * level.coerceIn(1, 10) / 5.0 }

        val target = when {
            cadence == 0 -> 0.0
            raw != null -> {
                val slowRaw = ema(cadSlow, raw, dt, LAG_S).also { cadSlow = it }
                if (hrPower != null) {
                    val slowHr = ema(hrPowerSlow, hrPower, dt, HR_SMOOTH_S).also { hrPowerSlow = it }
                    // Nur bei gleichmaessigem Treten lernen - sonst ist das Verhaeltnis Unsinn.
                    if (cadence >= MIN_LEARN_CADENCE && slowRaw > 0.2) {
                        val wanted = (slowHr / slowRaw).coerceIn(ftp * 0.2, ftp * 2.5)
                        gain += (wanted - gain) * (1 - exp(-dt / LEARN_S))
                    }
                }
                gain * raw
            }
            hrPower != null -> ema(hrPowerSlow, hrPower, dt, HR_SMOOTH_S).also { hrPowerSlow = it }
            else -> 0.0
        }
        val smoothed = if (target == 0.0) 0.0 else ema(output, target, dt, OUTPUT_SMOOTH_S)
        output = smoothed
        return (smoothed * profile.calibrationPercent / 100.0).roundToInt().coerceIn(0, MAX_WATTS)
    }

    companion object {
        const val MAX_WATTS = 2000
        const val DEFAULT_LEVEL = 5
        /** Startwert: 90 U/min auf Stufe 5 entsprechen 60 % der FTP. */
        private const val DEFAULT_GAIN_OF_FTP = 0.6
        private const val LAG_S = 25.0
        private const val HR_SMOOTH_S = 10.0
        private const val LEARN_S = 90.0
        private const val OUTPUT_SMOOTH_S = 2.0
        private const val MIN_LEARN_CADENCE = 50

        /** Leistung, die zum Puls passt - ueber die Herzfrequenzreserve. */
        fun hrPower(heartRate: Int, profile: TrainerProfile): Double {
            val reserve = (profile.maxHr - profile.restHr).coerceAtLeast(20)
            val fraction = (heartRate - profile.restHr).toDouble() / reserve
            return (profile.ftp * (fraction - 0.15) / 0.70).coerceIn(0.0, profile.ftp * 1.5)
        }

        fun cadenceShape(cadence: Int): Double = (cadence.coerceIn(0, 200) / 90.0).pow(1.7)

        /**
         * Grobe FTP aus dem Koerper: Freizeitfahrer ~2,6 W/kg (Frauen 2,2), ab 35 Jahren
         * 0,5 % weniger pro Jahr. Ohne Gewicht wird mit 75 kg gerechnet.
         */
        fun estimateFtp(body: BodyProfile): Double {
            val kg = body.weightKg.takeIf { it > 0 } ?: 75.0
            val perKg = if (body.sex == Sex.FEMALE) 2.2 else 2.6
            val agePenalty = ((body.age ?: 35) - 35).coerceAtLeast(0) * 0.005
            return kg * perKg * (1 - agePenalty).coerceAtLeast(0.6)
        }

        private fun ema(prev: Double?, value: Double, dt: Double, tau: Double): Double =
            if (prev == null) value else prev + (value - prev) * (1 - exp(-dt / tau))
    }
}
