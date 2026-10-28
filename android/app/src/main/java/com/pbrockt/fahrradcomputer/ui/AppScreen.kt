package com.pbrockt.fahrradcomputer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/** Die Bereiche der App, in dieser Reihenfolge im Menue links. */
enum class AppScreen(val label: String, val icon: ImageVector) {
    RIDE("Computer", Icons.Filled.PhoneAndroid),
    RIDES("Verlauf", Icons.Filled.History),
    MAPS("Routen", Icons.Filled.Route),
    TRAINER("Heimtrainer", Icons.Filled.FitnessCenter),
    SENSORS("Sensoren", Icons.Filled.Bluetooth),
    SETTINGS("Einstellungen", Icons.Filled.Tune),
}
