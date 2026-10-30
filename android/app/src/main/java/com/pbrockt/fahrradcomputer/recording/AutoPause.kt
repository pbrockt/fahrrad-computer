package com.pbrockt.fahrradcomputer.recording

/**
 * Erkennt Stehen und Losrollen aus den GPS-Punkten: Wer [STOP_AFTER_MS] lang langsamer als
 * [STOP_KMH] ist (Ampel, Pause), wird pausiert; weiter geht es, sobald zwei Punkte
 * hintereinander mindestens [GO_KMH] zeigen - einer allein kann ein GPS-Sprung sein.
 * Ungenaue Punkte (> [MAX_ACCURACY_M]) zaehlen nicht.
 */
class AutoPause {
    private var slowSince: Long? = null
    private var fastCount = 0
    private var prev: LocationSample? = null

    /** Tempo in km/h: vom GPS gemeldet, sonst aus dem Abstand zum vorigen Punkt. */
    fun speedKmh(s: LocationSample): Double? {
        val p = prev
        prev = s
        s.speedMps?.let { return it * 3.6 }
        if (p == null || s.timeMs <= p.timeMs) return null
        return RideTracker.distanceMeters(p.lat, p.lon, s.lat, s.lon) / ((s.timeMs - p.timeMs) / 1000.0) * 3.6
    }

    /** Waehrend der Aufnahme: true = jetzt pausieren. */
    fun shouldPause(s: LocationSample): Boolean {
        val kmh = speedKmh(s) ?: return false
        if ((s.accuracy ?: 0f) > MAX_ACCURACY_M) return false
        if (kmh >= STOP_KMH) {
            slowSince = null
            return false
        }
        val since = slowSince ?: s.timeMs.also { slowSince = it }
        return s.timeMs - since >= STOP_AFTER_MS
    }

    /** Waehrend einer Auto-Pause: true = weiterfahren. */
    fun shouldResume(s: LocationSample): Boolean {
        val kmh = speedKmh(s) ?: return false
        if ((s.accuracy ?: 0f) > MAX_ACCURACY_M) return false
        fastCount = if (kmh >= GO_KMH) fastCount + 1 else 0
        return fastCount >= 2
    }

    fun reset() {
        slowSince = null
        fastCount = 0
    }

    companion object {
        const val STOP_KMH = 2.5
        const val GO_KMH = 6.0
        const val STOP_AFTER_MS = 5_000L
        const val MAX_ACCURACY_M = 30f
    }
}
