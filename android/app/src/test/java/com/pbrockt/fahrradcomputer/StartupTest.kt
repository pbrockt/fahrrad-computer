package com.pbrockt.fahrradcomputer

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Startet die echte App (Application + MainActivity) einmal komplett - faengt Abstuerze
 * beim Start ab, die die Einzeltests nicht sehen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StartupTest {

    @Test
    fun appStartsWithoutCrash() {
        // Abstuerze in Coroutinen landen beim Handler des Threads - auf dem Handy beendet das
        // die App, hier wuerden sie nur ins Log geschrieben. Deshalb einsammeln und pruefen.
        val crashes = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes += e }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                repeat(20) { ShadowLooper.idleMainLooper(); Thread.sleep(50) }
                scenario.onActivity { }
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
        if (crashes.isNotEmpty()) throw AssertionError("Absturz beim Start", crashes.first())
    }
}
