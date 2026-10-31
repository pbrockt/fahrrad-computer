# Live-Link – Server-Teil

Auf der Startseite (`https://dein-server.de/live/`, mit Passwort) startest du eine neue
Fahrt. Jede Fahrt bekommt einen eigenen **Link für Zuschauer** und einen eigenen
**Upload-Key** für die App – zum Kopieren, per WhatsApp oder als **QR-Code**, den die App
scannt. Am Handy übernimmt der Knopf „In der App übernehmen“ alles direkt.

Die App schickt während der Fahrt die Position an `api.php`. Wer den Link kennt, sieht
Karte, Strecke und Zahlen. Die Seite aktualisiert sich alle 15 Sekunden.

**Ohne Datenbank:** Je Fahrt liegen zwei Dateien im Ordner `data/`:
`s-<id>.csv` mit den Punkten (Zeit, Breite, Länge, Höhe, Puls) und `s-<id>.json` mit
Schlüsseln, Name, Zustand und Zahlen. Kein MySQL, kein SQLite, keine Tabellen. Nach 5 Tagen ohne Änderung
löscht der Server beide Dateien selbst (`keep_days`).

## Voraussetzungen

- PHP **8.1 oder neuer**, keine besonderen Erweiterungen.
- Der Ordner `data/` muss für PHP beschreibbar sein.
- HTTPS. Die App sendet einen geheimen Token mit, also bitte nicht über unverschlüsseltes `http://`.
- Der Server muss aus dem Internet erreichbar sein, denn das Handy sendet über mobile Daten.

## Einrichten

1. Den Ordner `live/` auf den Webserver kopieren, z. B. nach `https://dein-server.de/live/`.
2. `config.sample.php` nach `config.php` kopieren und bei `admin_password` ein Passwort
   eintragen (mindestens 8 Zeichen). Ohne `config.php` schlägt die Startseite eines vor und
   prüft PHP-Version und Ordner.
3. Optional in `config.php`:
   - `privacy_zones`: Kreis um die Haustür, in dem nie etwas gespeichert wird.
   - `keep_days`: Nach so vielen Tagen ohne Meldung wird eine Fahrt gelöscht (Standard 5).
   - `show_heart_rate`: Puls auf der Live-Seite zeigen.

## Für jede Fahrt

1. `https://dein-server.de/live/` öffnen, mit dem Passwort anmelden (der Browser merkt es sich).
2. **„Neue Fahrt starten“** tippen.
3. **Link für Zuschauer** kopieren oder per WhatsApp teilen.
4. In die App übernehmen:
   - am PC: in der App **Einstellungen → Live-Link → „QR-Code der Live-Seite scannen“**
   - am Handy: **„In der App übernehmen“** tippen
   - oder den Upload-Key kopieren und in der App eintragen
5. Die App fragt kurz nach, speichert Adresse und Key, schaltet den Live-Link ein und testet
   die Verbindung.

Feste Schlüssel aus älteren Versionen (`upload_token`/`view_key` in `config.php`) gelten
weiter als Dauer-Fahrt.

### Ordner `data/` schützen

- **Apache** (auch goneo): `data/.htaccess` sperrt den Ordner schon.
- **nginx:** In die Server-Konfiguration eintragen:
  ```nginx
  location ~ ^/live/(data/|config\.php|lib\.php) { deny all; }
  ```
- **Am sichersten:** In `config.php` unter `data_dir` einen Ordner außerhalb des Web-Ordners angeben.

### Bei einem Webhoster (z. B. goneo)

1. Im Kundencenter für die Domain **PHP 8.1 oder neuer** einstellen und ein **SSL-Zertifikat**
   (Let's Encrypt) aktivieren.
2. Den Ordner `live/` per FTP/SFTP hochladen, auch die versteckte Datei `data/.htaccess`.
   Viele FTP-Programme zeigen Dateien mit Punkt erst nach einer Einstellung an.
3. Dem Ordner `data/` die Rechte **755** geben. Meldet die Seite „nicht beschreibbar“, **775**
   versuchen.
4. Weiter wie oben unter „Einrichten“ und „Für jede Fahrt“.
5. Zum Prüfen `https://deine-domain.de/live/data/` öffnen: Es muss „Forbidden“ / 403 kommen.

Datenbank, SQL-Datei oder besondere Rechte braucht es nicht.

### Auf dem Unraid-Server

Jeder Container mit Webserver und PHP geht, z. B. „nginx + PHP“ oder „Apache + PHP“ aus den
Community Apps. Erreichbar von außen wird er über den vorhandenen Reverse-Proxy
(Nginx Proxy Manager, SWAG, Cloudflare Tunnel) mit HTTPS.

## Ausprobieren ohne App

```bash
URL=https://dein-server.de/live
TOKEN=upload-key-von-der-startseite

# Verbindung testen – Antwort enthält den Link für Zuschauer
curl -s -X POST "$URL/api.php" -H "Authorization: Bearer $TOKEN" -d '{"ping":true}'

# Eine Test-Position senden
curl -s -X POST "$URL/api.php" -H "Authorization: Bearer $TOKEN" \
  -d '{"ride":"test-1","name":"Test","state":"riding","stats":{"distanceM":1200,"movingMs":300000,"speedKmh":22.5,"avgKmh":20.1,"ascentM":15},
       "points":[{"t":'$(date +%s000)',"lat":50.3512,"lon":7.5981}]}'
```

Danach den Link für Zuschauer von der Startseite im Browser öffnen.

## Was gespeichert wird

- Je Fahrt: Name, Zustand (unterwegs/Pause/angekommen) und die Zahlen.
- Je Punkt: Zeit, Position, Höhe, Puls nur wenn in der App eingeschaltet.

Die App sendet die ersten 500 m nicht (abschaltbar), damit der Wohnort nicht zu sehen ist.
Alte Fahrten löscht der Server nach `keep_days` (Standard 5 Tage) – beim nächsten Upload
oder Aufruf der Live-Seite, ein Cron-Job ist nicht nötig.

## Schnittstelle

| Aufruf | Zweck |
|---|---|
| `POST api.php` mit `Authorization: Bearer <Upload-Key>` (oder `X-Live-Token`) | `{"ping":true}` oder Fahrtdaten (siehe oben). Antwort `{"ok":true,"view":"…"}` |
| `GET api.php?k=<Zuschauer-Schlüssel>&since=<n>` | Neueste Fahrt mit allen Punkten nach Zeile `n` der CSV; die Route nur bei `since=0` |
