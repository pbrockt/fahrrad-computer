package com.pbrockt.fahrradcomputer

import android.app.Application
import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.data.RideRepository
import com.pbrockt.fahrradcomputer.data.SettingsStore
import com.pbrockt.fahrradcomputer.health.HealthConnect
import com.pbrockt.fahrradcomputer.recording.RideSession
import com.pbrockt.fahrradcomputer.sensors.SensorHub
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Haelt die Teile, die laenger leben als ein Bildschirm: Einstellungen, Sensoren und die
 * laufende Fahrt. Activity und [com.pbrockt.fahrradcomputer.recording.RideService]
 * greifen auf dieselben Instanzen zu.
 */
class FahrradApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var settings: SettingsStore
        private set
    lateinit var currentSettings: StateFlow<AppSettings>
        private set
    lateinit var sensors: SensorHub
        private set
    lateinit var ride: RideSession
        private set
    lateinit var health: HealthConnect
        private set
    lateinit var rides: RideRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        currentSettings = settings.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
        sensors = SensorHub(this, scope)
        health = HealthConnect(this)
        val ridesDir = java.io.File(filesDir, "fahrten").apply { mkdirs() }
        rides = RideRepository(ridesDir, health) { currentSettings.value }
        ride = RideSession(this, scope, { currentSettings.value }, rides)

        scope.launch {
            // Erst wenn die Einstellungen geladen sind - aeltere Fahrten brauchen sie fuer die Kennzahlen.
            settings.settings.first()
            rides.recoverInterrupted()
            rides.refresh()
            importWeight()
        }

        // Gespeicherte Sensoren beim Start (und nach jeder Aenderung) verbinden.
        for (kind in SensorKind.entries) {
            scope.launch {
                settings.settings
                    .distinctUntilChangedBy { it.device(kind) }
                    .collect { s ->
                        val d = s.device(kind)
                        sensors.setTarget(kind, d?.address, d?.name)
                    }
            }
        }
    }

    /**
     * Uebernimmt das neueste Gewicht aus Health Connect (z. B. von einer Waage), wenn das
     * eingestellt und erlaubt ist. Gibt das Gewicht zurueck oder null.
     */
    suspend fun importWeight(force: Boolean = false): Double? {
        val s = settings.settings.first()
        if (!force && !s.healthWeightSync) return null
        val kg = health.latestWeightKg() ?: return null
        settings.setWeight(kg)
        return kg
    }
}
