# Fahrrad-Computer

Android-App (Kotlin + Jetpack Compose) als Fahrrad-Computer für ein E-Bike mit Bosch SX:
modularer Bildschirm mit Geschwindigkeit, Trittfrequenz, Puls in Zonenfarben und Navigation,
Kalorienberechnung, Streckenaufzeichnung, Health Connect, Live-Standort und KI-Trainingsplan.

Die geplante Vorgehensweise steht in [KONZEPT.md](KONZEPT.md).

## Stand: Phase 1 (0.1.0-alpha.1)

| Bereich | Stand |
|---|---|
| Dashboard | Kacheln: Geschwindigkeit, Puls in Zonenfarbe (Z1–Z5), Trittfrequenz (Platzhalter), Distanz, Fahrzeit, Ø km/h, Uhrzeit, Streckenskizze. Display bleibt an. Layout ist schon als Daten aufgebaut (`DashboardLayout`), der Editor folgt in Phase 2. |
| Pulsgurt | Jeder Bluetooth-Gurt mit Standard-Pulsprofil; Uhren, die den Puls senden (Garmin „HF senden“). Automatisches Wiederverbinden. |
| Aufzeichnung | GPS im Vordergrunddienst (läuft bei aus­geschaltetem Display weiter), Start/Pause/Weiter/Stopp, Standzeiten zählen nicht als Fahrzeit, Zwischenspeichern jede Minute. |
| Fahrten | Gespeichert als GPX mit Puls; teilen (Strava, Komoot, Trainingsauswertung) und löschen. |
| Einstellungen | Max. Herzfrequenz (Zonen), Gewicht, Update-Kanal Release/Alpha, GitHub-Token. |
| Updates | Kanal Release ignoriert Alphas, Kanal Alpha nimmt die höchste Version. Workflow `release.yml` baut bei Tag `x.y.z` bzw. `x.y.z-alpha.N`. |

Noch nicht enthalten: Trittfrequenz/Bosch, Kalorien, Health Connect, Live-Tracker, Karte, KI – siehe Phasen im Konzept.

## Bauen

```bash
cd android
./gradlew testDebugUnitTest assembleRelease
# APK: android/app/build/outputs/apk/release/app-release.apk
```

## Veröffentlichen (später)

1. `versionName`/`versionCode` in `android/app/build.gradle.kts` erhöhen
2. Commit, Tag = versionName (z. B. `0.1.0-alpha.2` oder `0.1.0`), Push mit Tag
3. GitHub Actions baut das APK und legt das (Pre-)Release an
