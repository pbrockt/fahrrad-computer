package com.pbrockt.fahrradcomputer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Die Bereiche der App, erreichbar ueber das Menue links. */
enum class AppScreen(val label: String, val icon: ImageVector) {
    RIDE("Fahren", Icons.AutoMirrored.Filled.DirectionsBike),
    RIDES("Fahrten & Training", Icons.AutoMirrored.Filled.List),
    MAPS("Karten & Routen", Icons.Filled.Map),
    SENSORS("Sensoren", Icons.Filled.Bluetooth),
    SETTINGS("Einstellungen", Icons.Filled.Settings),
}
