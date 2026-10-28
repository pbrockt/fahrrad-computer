package com.pbrockt.fahrradcomputer

import android.app.Application
import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.data.RideRepository
import com.pbrockt.fahrradcomputer.data.SettingsStore
import com.pbrockt.fahrradcomputer.coach.CoachStore
import com.pbrockt.fahrradcomputer.health.HealthConnect
import com.pbrockt.fahrradcomputer.maps.OfflineMaps
import com.pbrockt.fahrradcomputer.routes.RouteStore
import com.pbrockt.fahrradcomputer.trainer.PowerBroadcaster
import com.pbrockt.fahrradcomputer.trainer.TrainerSession
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
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
    lateinit var routes: RouteStore
        private set
    lateinit var offlineMaps: OfflineMaps
        private set
    lateinit var coach: CoachStore
        private set
    lateinit var trainer: TrainerSession
        private set
    val weather = com.pbrockt.fahrradcomputer.weather.WeatherRepository()
    lateinit var broadcaster: PowerBroadcaster
        private set

    override fun onCreate() {
        super.onCreate()
        // Mapsforge braucht das einmal pro Prozess, bevor eine Karte erzeugt wird.
        AndroidGraphicFactory.createInstance(this)
        settings = SettingsStore(this)
        currentSettings = settings.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
        sensors = SensorHub(this, scope)
        health = HealthConnect(this)
        val ridesDir = java.io.File(filesDir, "fahrten").apply { mkdirs() }
        rides = RideRepository(ridesDir, health) { currentSettings.value }
        ride = RideSession(this, scope, { currentSettings.value }, rides)
        routes = RouteStore(this)
        offlineMaps = OfflineMaps(this)
        coach = CoachStore(this)
        trainer = TrainerSession { currentSettings.value }
        broadcaster = PowerBroadcaster(this)
        scope.launch { coach.load() }

        scope.launch {
            routes.refresh()
            // Fehlende Hoehenprofile im Hintergrund nachladen (braucht Internet).
            launch { routes.fillMissingProfiles() }
            // Aktive Route an die Fahrt geben - auch wenn sie spaeter gewechselt wird.
            combine(settings.settings.map { it.activeRouteId }.distinctUntilChanged(), routes.routes) { id, _ ->
                routes.byId(id)
            }.collect { ride.setRoute(it) }
        }
        // Wetter am aktuellen Standort, waehrend der Fahrt (laedt hoechstens alle 15 min / 5 km neu).
        scope.launch {
            ride.lastPoint.collect { p ->
                if (p != null && currentSettings.value.weatherEnabled) weather.update(p.lat, p.lon)
            }
        }
        // Downloads, die im Hintergrund fertig wurden, uebernehmen.
        scope.launch { offlineMaps.refresh() }

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

    /** Merkt sich, was der Heimtrainer ueber Trittfrequenz und Watt gelernt hat. */
    fun saveTrainerGain() {
        val gain = trainer.learnedGain
        if (gain > 0) scope.launch { settings.setTrainerGain(gain) }
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
