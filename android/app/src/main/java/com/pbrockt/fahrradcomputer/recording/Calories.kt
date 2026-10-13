package com.pbrockt.fahrradcomputer.recording

enum class Sex { MALE, FEMALE }

/** Was die Kalorienformeln ueber den Fahrer wissen muessen. */
data class BodyProfile(
    val weightKg: Double = 0.0,
    val age: Int? = null,
    val sex: Sex? = null,
)

/** Woher der Kalorienwert gerade kommt - die Kachel zeigt es an. */
enum class CalorieSource(val label: String) {
    POWER("Leistung"),
    HEART_RATE("Puls"),
    ESTIMATE("Schätzung"),
    NONE("–"),
}

/**
 * Kalorienverbrauch pro Sekunde, mit der genauesten Quelle, die gerade da ist:
 *
 * 1. **Leistung** (Watt vom Leistungsmesser bzw. Bosch-Fahrerleistung): kcal ≈ kJ.
 *    Der Mensch setzt nur ~24 % der Nahrungsenergie in Tretleistung um; das gleicht den
 *    Umrechnungsfaktor 4,184 kJ/kcal fast genau aus. Beim E-Bike zaehlt nur die eigene
 *    Leistung - der Motor verbrennt keine Kalorien.
 * 2. **Puls** (Keytel et al. 2005): braucht Gewicht, Alter und Geschlecht.
 * 3. **Schaetzung** ueber MET-Werte nach Geschwindigkeit. Bewusst niedrig angesetzt,
 *    weil der Motor beim E-Bike einen grossen Teil der Arbeit uebernimmt.
 */
object Calories {

    fun perSecond(
        powerWatts: Int?,
        heartRate: Int?,
        speedKmh: Double,
        moving: Boolean,
        body: BodyProfile,
    ): Pair<Double, CalorieSource> {
        if (powerWatts != null) {
            return powerWatts.coerceAtLeast(0) / 1000.0 to CalorieSource.POWER
        }
        val age = body.age
        val sex = body.sex
        if (heartRate != null && body.weightKg > 0 && age != null && sex != null) {
            val w = body.weightKg
            val kjPerMin = when (sex) {
                Sex.MALE -> -55.0969 + 0.6309 * heartRate + 0.1988 * w + 0.2017 * age
                Sex.FEMALE -> -20.4022 + 0.4472 * heartRate - 0.1263 * w + 0.074 * age
            }
            return (kjPerMin / 4.184 / 60).coerceAtLeast(0.0) to CalorieSource.HEART_RATE
        }
        if (body.weightKg > 0) {
            if (!moving) return 0.0 to CalorieSource.ESTIMATE
            return met(speedKmh) * body.weightKg / 3600 to CalorieSource.ESTIMATE
        }
        return 0.0 to CalorieSource.NONE
    }

    /** MET-Werte fuers E-Bike (unterstuetztes Fahren), grob nach Geschwindigkeit. */
    fun met(speedKmh: Double): Double = when {
        speedKmh < 16 -> 3.5
        speedKmh < 20 -> 4.5
        speedKmh < 25 -> 5.5
        else -> 6.5
    }
}
