package com.pbrockt.fahrradcomputer

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.pbrockt.fahrradcomputer.data.HrZones
import com.pbrockt.fahrradcomputer.data.Zones
import com.pbrockt.fahrradcomputer.recording.CalorieSource
import com.pbrockt.fahrradcomputer.recording.Climb
import com.pbrockt.fahrradcomputer.recording.GpsStatus
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.recording.RideStats
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RoutePoint
import com.pbrockt.fahrradcomputer.routes.RouteProgress
import com.pbrockt.fahrradcomputer.sensors.SensorConnection
import com.pbrockt.fahrradcomputer.sensors.SensorReading
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.FahrradTheme
import com.pbrockt.fahrradcomputer.ui.ThemeMode
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardLayout
import com.pbrockt.fahrradcomputer.ui.dashboard.DashboardPage
import com.pbrockt.fahrradcomputer.ui.dashboard.RowHeight
import com.pbrockt.fahrradcomputer.ui.dashboard.TileRow
import com.pbrockt.fahrradcomputer.ui.dashboard.TileSlot
import com.pbrockt.fahrradcomputer.ui.dashboard.TileType
import com.pbrockt.fahrradcomputer.ui.dashboard.PageDashes
import com.pbrockt.fahrradcomputer.ui.dashboard.PageView
import com.pbrockt.fahrradcomputer.ui.dashboard.RideControls
import com.pbrockt.fahrradcomputer.ui.dashboard.TileData
import com.pbrockt.fahrradcomputer.ui.dashboard.TopBar
import com.pbrockt.fahrradcomputer.weather.Weather
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

/**
 * Rendert die Standard-Tacho-Seiten mit Beispielwerten als PNG - zum Pruefen des Designs
 * ohne Handy. Laeuft nur auf Wunsch:
 * `./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Dscreenshots=/pfad/zum/ordner`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val outDir: String? = System.getProperty("screenshots")?.takeIf { it.isNotBlank() }

    /** Ausgedachte Runde mit Huegel - keine echten Daten. */
    private val route = Route(
        "demo", "Demo-Runde",
        (0..200).map { i -> RoutePoint(50.0 + i * 0.0003, 7.0 + sin(i / 20.0) * 0.002, 120.0 + 60 * sin(i / 40.0) + i * 0.2) },
    )
    private val track = (0..120).map { i ->
        TrackPoint(1_000_000L + i * 5_000L, 50.0 + i * 0.0002, 7.0 + sin(i / 15.0) * 0.001, 130.0, 120 + (i % 40))
    }

    private fun data(now: Long) = TileData(
        stats = RideStats(
            distanceM = 18_420.0, movingTimeMs = 2_843_000, speedKmh = 24.6, maxSpeedKmh = 41.2, avgHr = 138, maxHr = 171,
            altitude = 214.0, avgCadence = 78, avgPower = 162, maxPower = 455, calories = 512.0,
            calorieSource = CalorieSource.HEART_RATE,
        ),
        hr = SensorReading(SensorConnection.CONNECTED, "Gurt", battery = 64, value = 152, updatedAtMs = now),
        cadence = SensorReading(SensorConnection.CONNECTED, "Tritt", battery = 15, value = 84, updatedAtMs = now),
        power = SensorReading(SensorConnection.CONNECTED, "Bosch", value = 203, updatedAtMs = now),
        zones = HrZones(180),
        now = now,
        route = track,
        phase = RidePhase.RECORDING,
        activeRoute = route,
        progress = RouteProgress(5.0, route.lengthM * 0.15, route.lengthM * 0.85),
        powerZones = Zones.power(210.0),
        climb = Climb(ascentM = 384.0, gradePct = 4.3),
        weather = Weather(now, 50.0, 7.0, 12.0, 18.0, 250.0, 31.0, 3),
        heading = 20f,
        weightKg = 80.0,
        ftp = 210.0,
        phoneBattery = 57,
    )

    private fun shoot(
        name: String, page: Int, mode: ThemeMode, phase: RidePhase = RidePhase.RECORDING,
        layout: DashboardLayout = DashboardLayout.DEFAULT,
    ) {
        val now = track.last().timeMs
        compose.setContent {
            FahrradTheme(mode) {
                Column(Modifier.fillMaxSize().background(AppColors.Bg)) {
                    TopBar(
                        phase, GpsStatus.OK, layout.pages[page].name,
                        listOf(data(now).hr, data(now).cadence, data(now).power), onMenu = {},
                        controls = RideControls({}, {}, {}, {}),
                    )
                    PageView(layout.pages[page], data(now), "test", Modifier.weight(1f).fillMaxWidth())
                    PageDashes(layout.pages.size + 1, page + 1)
                }
            }
        }
        compose.waitForIdle()
        // captureToImage haengt unter Robolectric - die Ansicht direkt in ein Bild zeichnen.
        val view = compose.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(outDir!!).mkdirs()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun ridePageDark() {
        assumeTrue(outDir != null)
        shoot("fahrt-dunkel", 0, ThemeMode.DARK)
    }

    @Test
    fun ridePageLight() {
        assumeTrue(outDir != null)
        shoot("fahrt-hell", 0, ThemeMode.LIGHT)
    }

    @Test
    fun climbPageLight() {
        assumeTrue(outDir != null)
        shoot("berg-hell", 2, ThemeMode.LIGHT)
    }

    @Test
    fun sectionsTileDark() {
        assumeTrue(outDir != null)
        val page = DashboardPage(
            "Anstiege",
            listOf(
                TileRow(listOf(TileSlot(TileType.CLIMB_SECTIONS)), RowHeight.LARGE),
                TileRow(listOf(TileSlot(TileType.CLIMB_SECTIONS)), RowHeight.MEDIUM),
                TileRow(listOf(TileSlot(TileType.CLIMB_SECTIONS), TileSlot(TileType.GRADE)), RowHeight.SMALL),
            ),
        )
        shoot("anstiege-dunkel", 0, ThemeMode.DARK, layout = DashboardLayout(listOf(page)))
    }

    @Test
    fun powerPageDark() {
        assumeTrue(outDir != null)
        shoot("leistung-dunkel", 3, ThemeMode.DARK)
    }

    @Test
    fun navPageIdleLight() {
        assumeTrue(outDir != null)
        shoot("navi-start-hell", 1, ThemeMode.LIGHT, RidePhase.IDLE)
    }

    @Test
    fun overviewPageDark() {
        assumeTrue(outDir != null)
        shoot("uebersicht-dunkel", 4, ThemeMode.DARK)
    }
}
