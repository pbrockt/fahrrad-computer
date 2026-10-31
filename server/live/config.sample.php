<?php
// Fahrrad-Computer Live-Link - Einstellungen.
// Diese Datei nach config.php kopieren und ein Passwort eintragen. Mehr ist nicht noetig:
// Link und Upload-Key entstehen je Fahrt auf der Startseite (https://dein-server/live/).

return [
    // Passwort fuer die Startseite (mindestens 8 Zeichen). Wer es kennt, kann Fahrten anlegen.
    'admin_password' => 'HIER-EIN-PASSWORT-EINTRAGEN',

    // Optional, aus aelteren Versionen: feste Schluessel fuer eine Dauer-Fahrt.
    // Leer lassen, wenn du die Schluessel je Fahrt auf der Startseite erzeugst.
    // 'upload_token' => '',
    // 'view_key' => '',

    // Ueberschrift der Live-Seite.
    'title' => 'Live-Fahrt',

    // Alte Fahrten nach so vielen Tagen loeschen (Datenschutz). 0 = nie.
    'keep_days' => 5,

    // Puls auf der Live-Seite zeigen (nur, wenn die App ihn mitsendet).
    'show_heart_rate' => false,

    // Bereiche, in denen keine Positionen gespeichert werden - z. B. rund um die Haustuer.
    // Jeweils [Breite, Laenge, Radius in Metern]. Beispiel: [[50.3512, 7.5981, 400]]
    'privacy_zones' => [],

    // Ordner fuer die Fahrten (je Fahrt eine CSV- und eine JSON-Datei). Muss fuer PHP
    // beschreibbar sein. Am besten ausserhalb des Web-Ordners; sonst schuetzt
    // data/.htaccess (Apache) - bei nginx siehe README.
    'data_dir' => __DIR__ . '/data',
];
