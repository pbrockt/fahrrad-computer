package com.pbrockt.fahrradcomputer.trainer

import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.recording.Calories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live-Werte und Summen einer Heimtrainer-Einheit. */
data class TrainerStats(
    val running: Boolean = false,
    val startedAtMs: Long = 0,
    val elapsedMs: Long = 0,
    /** Zeit, in der getreten wurde. */
    val pedalingMs: Long = 0,
    val watts: Int = 0,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val calories: Double = 0.0,
    val energyKj: Double = 0.0,
    val maxWatts: Int = 0,
    /** Woher die Watt gerade kommen - fuer den Hinweis auf dem Bildschirm. */
    val source: TrainerSource = TrainerSource.NONE,
) {
    val avgWatts: Int get() = if (pedalingMs > 0) (energyKj * 1_000_000 / pedalingMs).toInt() else 0
}

enum class TrainerSource(val label: String) {
    BOTH("Puls + Trittfrequenz"),
    CADENCE("nur Trittfrequenz (ohne Puls ungenau)"),
    HEART_RATE("nur Puls (reagiert verzögert)"),
    NONE("keine Sensoren"),
}

/**
 * Heimtrainer-Modus: rechnet einmal pro Sekunde virtuelle Watt aus Puls und Trittfrequenz
 * und summiert Zeit, Energie und Kalorien. Das Senden an Zwift macht
 * [TrainerService] mit dem [PowerBroadcaster]; diese Klasse rechnet nur.
 */
class TrainerSession(private val settings: () -> AppSettings) {

    private val _stats = MutableStateFlow(TrainerStats())
    val stats: StateFlow<TrainerStats> = _stats.asStateFlow()

    private var model = VirtualPower()
    private var lastMs = 0L

    val learnedGain: Double get() = model.gain

    fun start(nowMs: Long) {
        val s = settings()
        model = VirtualPower(initialGain = s.trainerGain)
        lastMs = 0L
        _stats.value = TrainerStats(running = true, startedAtMs = nowMs)
    }

    fun stop() {
        _stats.value = _stats.value.copy(running = false, watts = 0)
    }

    /** Ein Messschritt; gibt die aktuellen Watt zurueck. */
    fun tick(nowMs: Long, heartRate: Int?, cadence: Int?): Int {
        val cur = _stats.value
        if (!cur.running) return 0
        val s = settings()
        val dtMs = if (lastMs == 0L) 0L else (nowMs - lastMs).coerceIn(0, 5_000)
        lastMs = nowMs
        val profile = profileOf(s)
        val watts = model.update(nowMs, heartRate, cadence, s.trainerLevel, profile)
        val pedaling = (cadence ?: 0) > 0 || (cadence == null && watts > 0)
        val (kcalPerS, _) = Calories.perSecond(watts, heartRate, 0.0, pedaling, s.body)
        val dtS = dtMs / 1000.0
        _stats.value = cur.copy(
            elapsedMs = cur.elapsedMs + dtMs,
            pedalingMs = cur.pedalingMs + if (pedaling) dtMs else 0,
            watts = watts,
            heartRate = heartRate,
            cadence = cadence,
            calories = cur.calories + kcalPerS * dtS,
            energyKj = cur.energyKj + watts * dtS / 1000,
            maxWatts = maxOf(cur.maxWatts, watts),
            source = when {
                heartRate != null && cadence != null -> TrainerSource.BOTH
                cadence != null -> TrainerSource.CADENCE
                heartRate != null -> TrainerSource.HEART_RATE
                else -> TrainerSource.NONE
            },
        )
        return watts
    }

    companion object {
        fun profileOf(s: AppSettings) = TrainerProfile(
            body = s.body,
            maxHr = s.maxHr,
            restHr = s.restHr,
            ftpWatts = s.trainerFtp,
            calibrationPercent = s.trainerCalibration,
        )
    }
}
