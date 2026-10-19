# Fahrrad-Computer – Konzept & Vorgehensweise

Android-App als Fahrrad-Computer für ein E-Bike mit **Bosch SX (Smart System)**.
Technik: **Kotlin + Jetpack Compose** (wie die App *Trainingsauswertung*).

---

## 1. Ziel & Funktionsumfang

| Funktion | Priorität |
|---|---|
| Modularer Bildschirm: Geschwindigkeit, Trittfrequenz, Puls in Zonenfarbe, Navi-Bereich | **Muss** |
| Puls über Brustgurt (BLE) und Armbanduhr (wenn sie Puls sendet) | **Muss** |
| Trittfrequenz über Bosch und/oder externen Sensor | **Muss** |
| GPS-Streckenaufzeichnung | **Muss** |
| Kalorienberechnung aus Gewicht, Puls, Fahrerleistung | **Muss** |
| Speichern in Health Connect | **Muss** |
| Auswertung und Zusammenfassung in der App | **Muss** |
| Live-Standort alle 10 min (einstellbar kürzer) per SMS, Telegram, Discord, E-Mail | **Muss** |
| Karte/Navigation mit GPX-Route | Soll |
| KI-Trainingsplan über die Claude-API | Soll |
| Bosch-Motorwerte anzeigen (Unterstützungsstufe, Akku) | Kann – hängt von Bosch ab |
| Live-Verfolgung auf eigener Webseite | Kann (später) |

---

## 2. Architektur

Eine App, intern in Bereiche aufgeteilt:

| Bereich | Aufgabe |
|---|---|
| `sensors/` | Bluetooth-LE-Verbindungen: Herzfrequenz (Service `0x180D`), Speed & Cadence (`0x1816`), Cycling Power (`0x1818`), Bosch. Automatisches Wiederverbinden. |
| `recording/` | Foreground-Service (läuft weiter bei ausgeschaltetem Display): GPS, Sensorwerte 1×/Sekunde, Auto-Pause, Speicherung in Room-Datenbank, Export als GPX/FIT. |
| `dashboard/` | Der modulare Bildschirm (siehe unten). |
| `navigation/` | Karte (MapLibre oder osmdroid mit OpenStreetMap, offline-fähig), GPX-Route importieren und anzeigen. Abbiegehinweise später (BRouter/GraphHopper). |
| `calories/` | Kalorienberechnung (Abschnitt 3). |
| `health/` | Health Connect: schreiben und Gewicht lesen. |
| `livetracking/` | Live-Standort verschicken (Abschnitt 5). |
| `analysis/` | Fahrtenliste, Detailansicht, Pulszonen-Verteilung, Wochen-/Monatssummen. |
| `coach/` | KI-Trainingsplan (Abschnitt 6). |
| `update/` | Update-Prüfung mit Alpha-/Release-Kanal (Abschnitt 9). |

### Modularer Bildschirm

- Raster aus **Kacheln**, frei belegbar: Geschwindigkeit, Trittfrequenz, Puls, Leistung, Akku,
  Unterstützungsstufe, Distanz, Fahrzeit, Kalorien, Höhe, Durchschnitt, **Navi-Karte**.
- **Puls-Kachel** färbt sich nach Zone (Z1 grau, Z2 blau, Z3 grün, Z4 orange, Z5 rot);
  Zonen über maximale Herzfrequenz einstellbar.
- Mehrere **Seiten** per Wischen (z. B. „Fahrt“, „Karte“, „Motor“).
- Layout per langem Druck bearbeiten (Kachel tauschen, Größe 1×1 / 2×1 / 2×2), gespeichert als JSON.
- Display bleibt an, große Schrift, hoher Kontrast für Sonnenlicht, Hell-/Dunkelmodus.

### Health Connect

Pro Fahrt eine `ExerciseSessionRecord` (Radfahren, mit Route) plus `HeartRateRecord`,
`SpeedRecord`, `CyclingPedalingCadenceRecord`, `PowerRecord`, `DistanceRecord`,
`ActiveCaloriesBurnedRecord`. Gewicht wird aus `WeightRecord` gelesen.

---

## 3. Kalorienberechnung

Reihenfolge nach Genauigkeit, die App nimmt automatisch die beste verfügbare Quelle:

1. **Fahrerleistung (Watt)** – das Bosch Smart System misst die Eigenleistung des Fahrers
   (nicht die des Motors). Faustregel: **kcal ≈ geleistete kJ**
   (Wirkungsgrad Mensch ~24 % gleicht den Umrechnungsfaktor 4,184 aus).
2. **Puls** – Keytel-Formel aus Gewicht, Alter, Geschlecht, Puls und Zeit.
3. **Schätzung** – MET-Wert nach Geschwindigkeit und Unterstützungsstufe × Gewicht × Zeit
   (Motorunterstützung senkt den Aufwand).

Gewicht: manuell oder automatisch aus Health Connect.

---

## 4. Bosch SX / Smart System – wichtigster Risikopunkt

- Das Smart System kann sich mit Fahrradcomputern (Garmin, Wahoo) per Bluetooth verbinden und
  liefert vermutlich Geschwindigkeit, Trittfrequenz und Fahrerleistung über Standard-Profile.
  Unterstützungsstufe und Akkustand möglicherweise nur über nicht dokumentierte Daten.
- **Motor-Einstellungen ändern** (Modi, Tuning) geht nur über Boschs eBike-Flow-App –
  es gibt keine offizielle Schnittstelle. Unsere App kann Motorwerte höchstens **anzeigen**.
- **Schritt 0:** Mit der App „nRF Connect“ das Rad scannen und notieren, welche Dienste es anbietet.
- **Fallback:** externer Trittfrequenz-/Speedsensor (BLE CSC), z. B. von Garmin, Wahoo, Magene.

**Armbanduhr:** funktioniert nur, wenn sie den Puls per Bluetooth sendet
(Garmin: „Herzfrequenz senden“). Brustgurt ist genauer und die Standardquelle.

---

## 5. Live-Tracker

- Intervall einstellbar: **1–60 Minuten** (Standard 10).
- Kanäle einzeln aktivierbar, auch mehrere gleichzeitig:
  - **SMS** (an eingetragene Nummern, Berechtigung nötig)
  - **Telegram** (Bot-Token + Chat-ID)
  - **Discord** (Webhook-URL)
  - **E-Mail** (SMTP-Zugang)
- Nachricht: Kartenlink (OSM/Google Maps), Uhrzeit, Geschwindigkeit, gefahrene km, Handy-Akku.
- Optional Start-/Ende-Nachricht und Notfall-Hinweis bei langem Stillstand.
- Technisch: Interface `LocationSender`, je Kanal eine Umsetzung; bei fehlendem Netz wird
  nachgeholt.
- **Später:** `WebSender` an einen eigenen Server (z. B. auf dem Unraid-Server) mit Live-Karte im
  Browser und teilbarem Link.

---

## 6. KI-Trainingsplan

- Über die **Claude-API** mit eigenem API-Key (console.anthropic.com). Abgerechnet wird pro
  Nutzung; bei gelegentlich erstellten Plänen sind die Kosten gering.
- Die App schickt eine Zusammenfassung (letzte Wochen: Fahrten, Pulszonen, Leistung, Gewicht,
  Ziel, verfügbare Trainingstage) und bekommt einen Wochenplan als JSON zurück.
- Anzeige als Wochenplan, Einheiten abhaken, Plan nach jeder Woche anpassen lassen.
- API-Key wird verschlüsselt auf dem Gerät gespeichert (Android Keystore), **nie im Repo**.

---

## 7. Vergleichs-App

_Hier eintragen, an welcher App wir uns orientieren (Screenshots, Lieblingsfunktionen,
was stört)._ Mögliche Vorbilder: Wahoo ELEMNT, Garmin Edge, Bosch eBike Flow, Strava, OsmAnd.

---

## 8. Phasen & Zeitschätzung

Entwicklung mit Claude, Tests am Rad durch Phillipp. Aufwand in Sitzungen à 1–3 Stunden:

| Phase | Inhalt | Sitzungen |
|---|---|---|
| 0 | Bosch per nRF Connect prüfen, Vergleichs-App sichten | 1 |
| 1 | Grundgerüst, Brustgurt-Puls, GPS-Aufzeichnung, einfaches Dashboard, Release-Pipeline | 2–3 |
| 2 | Modularer Bildschirm (Kacheln, Zonenfarben, Layout-Editor) | 2–3 |
| 3 | Trittfrequenz/Leistung (Bosch + externer Sensor), Kalorien | 2–4 |
| 4 | Speicherung, Auswertung, Health Connect | 2–3 |
| 5 | Live-Tracker (SMS, Telegram, Discord, E-Mail) | 2 |
| 6 | Karte/Navigation mit GPX | 2–3 |
| 7 | KI-Trainingsplan | 1–2 |
| 8 | Live-Webseite (optional) | 2–4 |
| 9 | Heimtrainer: virtuelle Watt aus Puls + Trittfrequenz, per Bluetooth an Zwift/Rouvy/MyWhoosh (0.0.13) | 1 |

- **Nutzbare Grundversion** (Phasen 0–2): ca. **1–2 Wochen** Freizeit.
- **Kompletter Umfang:** realistisch **4–8 Wochen**.
- Größte Unsicherheiten: Bosch-Schnittstelle und die Testfahrten.
  Eine Vergleichs-App spart Zeit, weil das Design klar vorgegeben ist.

---

## 9. Release: Alpha- und Release-Kanal

- GitHub Actions baut bei jedem Tag automatisch die APK und erstellt das GitHub-Release.
- **Release:** Tag `x.y.z` → normales Release.
- **Alpha:** Tag `x.y.z-alpha.N` → Release mit Markierung **„Pre-release“**
  (der Workflow setzt `prerelease: true`, wenn der Tag `-alpha` enthält).
- In der App: Einstellung **„Update-Kanal: Release / Alpha“** (Standard: Release).
  - Beide Kanäle lesen die Release-Liste (`/releases`).
  - **Release-Kanal** nimmt nur reguläre Releases – alles mit Pre-Release-Markierung oder
    `-alpha` im Tag wird ignoriert.
  - **Alpha-Kanal** nimmt die höchste Version, egal ob Alpha oder Release
    (Vergleich nach SemVer: `1.2.0-alpha.3` < `1.2.0`).
- `versionCode` steigt bei jedem Build (auch bei Alphas), damit Android das Update installiert.
- Das Repo ist privat: Wie bei der Trainingsauswertung wird in den App-Einstellungen ein
  GitHub-Token mit Leserecht hinterlegt (nur auf dem Gerät gespeichert).

---

## 10. Datenschutz

- Alle Fahrtdaten bleiben lokal auf dem Handy bzw. in Health Connect.
- Live-Standort geht nur an selbst eingetragene Empfänger und nur während einer Fahrt.
- API-Keys und Tokens nur verschlüsselt auf dem Gerät.
