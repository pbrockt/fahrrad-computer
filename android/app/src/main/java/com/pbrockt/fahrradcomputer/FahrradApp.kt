package com.pbrockt.fahrradcomputer

import android.app.Application
import com.pbrockt.fahrradcomputer.data.SettingsStore
import com.pbrockt.fahrradcomputer.recording.RideSession
import com.pbrockt.fahrradcomputer.sensors.HeartRateSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Haelt die Teile, die laenger leben als ein Bildschirm: Einstellungen, Pulsgurt und die
 * laufende Fahrt. Activity und [com.pbrockt.fahrradcomputer.recording.RideService]
 * greifen auf dieselben Instanzen zu.
 */
class FahrradApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var settings: SettingsStore
        private set
    lateinit var heartRate: HeartRateSensor
        private set
    lateinit var ride: RideSession
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        heartRate = HeartRateSensor(this, scope)
        ride = RideSession(this, scope)

        // Gespeicherten Pulsgurt beim Start (und nach jeder Aenderung) verbinden.
        scope.launch {
            settings.settings
                .distinctUntilChangedBy { it.hrDeviceAddress }
                .collect { heartRate.setTarget(it.hrDeviceAddress, it.hrDeviceName) }
        }
    }
}
