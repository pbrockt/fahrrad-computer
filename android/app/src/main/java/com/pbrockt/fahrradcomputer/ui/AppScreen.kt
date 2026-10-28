package com.pbrockt.fahrradcomputer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Die Bereiche der App. Hier liegen die Hauptbereiche als Reiter unten
 * ([tab] = true); Heimtrainer und Sensoren sind Unterseiten mit Zurueck-Pfeil.
 */
enum class AppScreen(val label: String, val icon: ImageVector, val tab: Boolean, val parent: AppScreen? = null) {
    RIDES("Verlauf", Icons.Filled.History, tab = true),
    RIDE("Computer", Icons.Filled.PhoneAndroid, tab = true),
    MAPS("Routen", Icons.Filled.Route, tab = true),
    SETTINGS("Einstellungen", Icons.Filled.Tune, tab = true),
    TRAINER("Heimtrainer", Icons.Filled.FitnessCenter, tab = false, parent = RIDE),
    SENSORS("Sensoren", Icons.Filled.Bluetooth, tab = false, parent = SETTINGS),
}
