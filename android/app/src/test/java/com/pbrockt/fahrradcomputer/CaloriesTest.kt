package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.BodyProfile
import com.pbrockt.fahrradcomputer.recording.CalorieSource
import com.pbrockt.fahrradcomputer.recording.Calories
import com.pbrockt.fahrradcomputer.recording.LocationSample
import com.pbrockt.fahrradcomputer.recording.RideTracker
import com.pbrockt.fahrradcomputer.recording.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

class CaloriesTest {

    private val body = BodyProfile(weightKg = 80.0, age = 40, sex = Sex.MALE)

    @Test
    fun powerWins() {
        val (rate, src) = Calories.perSecond(150, 140, 20.0, true, body)
        assertEquals(CalorieSource.POWER, src)
        // 150 W eine Stunde = 540 kJ ≈ 540 kcal
        assertEquals(540.0, rate * 3600, 0.01)
    }

    @Test
    fun heartRateKeytelMale() {
        val (rate, src) = Calories.perSecond(null, 140, 20.0, true, body)
        assertEquals(CalorieSource.HEART_RATE, src)
        // (-55.0969 + 0.6309*140 + 0.1988*80 + 0.2017*40) / 4.184 ≈ 13.67 kcal/min
        assertEquals(13.67, rate * 60, 0.01)
    }

    @Test
    fun heartRateNeedsAgeAndSex() {
        val (_, src) = Calories.perSecond(null, 140, 20.0, true, BodyProfile(80.0))
        assertEquals(CalorieSource.ESTIMATE, src)
    }

    @Test
    fun estimateOnlyWhileMoving() {
        val (moving, _) = Calories.perSecond(null, null, 22.0, true, body)
        assertEquals(5.5 * 80, moving * 3600, 0.01)
        assertEquals(0.0, Calories.perSecond(null, null, 0.0, false, body).first, 0.0)
    }

    @Test
    fun withoutWeightNothing() {
        assertEquals(CalorieSource.NONE, Calories.perSecond(null, null, 20.0, true, BodyProfile()).second)
    }

    @Test
    fun trackerIntegratesPowerOverRide() {
        val t = RideTracker(body)
        // 10 Minuten mit 200 W, ein GPS-Punkt pro Sekunde
        for (i in 0..600) {
            t.onPower(200, i * 1000L)
            t.onCadence(85, i * 1000L)
            t.onLocation(LocationSample(i * 1000L, 53.0 + i * 0.00005, 7.5, null, 5f, 5.5f))
        }
        val s = t.snapshot
        assertEquals(120.0, s.calories, 0.5) // 200 W * 600 s = 120 kJ
        assertEquals(CalorieSource.POWER, s.calorieSource)
        assertEquals(200, s.avgPower)
        assertEquals(85, s.avgCadence)
        assertEquals(85, t.trackPoints.last().cadence)
        assertEquals(200, t.trackPoints.last().power)
    }

    @Test
    fun zeroCadenceDoesNotLowerAverage() {
        val t = RideTracker()
        t.onCadence(90, 0)
        t.onCadence(0, 1_000)
        t.onCadence(80, 2_000)
        assertEquals(85, t.snapshot.avgCadence)
    }
}
