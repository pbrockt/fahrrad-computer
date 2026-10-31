<?php
// Fahrrad-Computer Live-Link - Schnittstelle.
//
// POST (die App):   Kopfzeile "Authorization: Bearer <Upload-Key>" (oder "X-Live-Token: ...")
//   {"ping": true}                                 -> {"ok":true,"view":"<Link fuer Zuschauer>"}
//   {"ride":"2026-10-28_08-30","name":"Phillipp","state":"riding|paused|finished",
//    "stats":{...}, "points":[{"t":ms,"lat":..,"lon":..,"ele":..,"hr":..}], "route":[[lat,lon],...]}
//                                                  -> {"ok":true,"stored":<Anzahl>,"view":"..."}
// GET (die Live-Seite):  api.php?k=<Zuschauer-Schluessel>&since=<letzte Punkt-Nummer>
//                                                  -> die Fahrt mit neuen Punkten
//
// Die Schluessel entstehen je Fahrt auf der Startseite (index.php). Gespeichert wird in
// data/s-<id>.csv (Zeile: t,lat,lon,ele,hr) und data/s-<id>.json. Die Punkt-Nummer ist die
// Zeilennummer in der CSV.

declare(strict_types=1);
require __DIR__ . '/lib.php';

$c = live_config();
if (!live_configured($c)) {
    live_json(['ok' => false, 'error' => 'Server noch nicht eingerichtet (config.php fehlt oder enthält Platzhalter).'], 503);
}

try {
    $dir = live_dir($c);
} catch (Throwable $e) {
    live_json(['ok' => false, 'error' => $e->getMessage()], 500);
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method === 'POST') {
    // Manche Server reichen "Authorization" nicht an PHP weiter - daher auch X-Live-Token.
    $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    $token = str_starts_with($auth, 'Bearer ') ? substr($auth, 7) : ($_SERVER['HTTP_X_LIVE_TOKEN'] ?? '');
    $session = live_session_by_key($dir, $c, trim((string)$token), 'upload');
    if ($session === null) {
        live_json(['ok' => false, 'error' => 'Upload-Key ungültig oder abgelaufen – auf der Live-Seite eine neue Fahrt starten.'], 401);
    }
    $raw = file_get_contents('php://input', false, null, 0, 2_000_000);
    $in = json_decode((string)$raw, true);
    if (!is_array($in)) {
        live_json(['ok' => false, 'error' => 'Kein gültiges JSON.'], 400);
    }
    if (!empty($in['ping'])) {
        live_json(['ok' => true, 'view' => live_view_url($session), 'version' => LIVE_VERSION]);
    }

    $ride = (string)($in['ride'] ?? '');
    if (!preg_match('/^[A-Za-z0-9_.:-]{1,40}$/', $ride)) {
        live_json(['ok' => false, 'error' => 'Ungültige Fahrt-Id.'], 400);
    }
    $state = in_array($in['state'] ?? '', ['riding', 'paused', 'finished'], true) ? $in['state'] : 'riding';
    $name = trim((string)($in['name'] ?? ''));
    $name = function_exists('mb_substr') ? mb_substr($name, 0, 40) : substr($name, 0, 40);
    $stats = is_array($in['stats'] ?? null) ? $in['stats'] : [];

    $id = (string)$session['id'];
    $metaFile = live_session_file($dir, $id, 'json');
    $csvFile = live_session_file($dir, $id, 'csv');

    // Eine Sperre je Fahrt: zwei Uploads gleichzeitig (nach Netzproblem) schreiben nacheinander.
    $lock = fopen($csvFile, 'a+');
    if ($lock === false) {
        live_json(['ok' => false, 'error' => 'Datei lässt sich nicht schreiben.'], 500);
    }
    flock($lock, LOCK_EX);

    // Unter der Sperre frisch lesen - die Fahrt kann sich seit eben geaendert haben.
    $meta = live_read_meta($metaFile) ?? $session;
    unset($meta['upload'], $meta['view']);
    if ($id !== 'standard') {
        $meta['upload'] = $session['upload'];
        $meta['view'] = $session['view'];
    }
    // Neue Fahrt in der App mit demselben Key: von vorn anfangen.
    if (($meta['rideId'] ?? null) !== null && $meta['rideId'] !== $ride) {
        ftruncate($lock, 0);
        $meta = array_merge($meta, ['lastT' => 0, 'count' => 0, 'route' => null]);
    }
    $meta['rideId'] = $ride;

    // Geplante Route nur einmal schicken lassen (beim Start); hoechstens 3000 Punkte.
    if (is_array($in['route'] ?? null)) {
        $route = [];
        foreach (array_slice($in['route'], 0, 3000) as $p) {
            if (is_array($p) && isset($p[0], $p[1]) && is_numeric($p[0]) && is_numeric($p[1])) {
                $route[] = [round((float)$p[0], 5), round((float)$p[1], 5)];
            }
        }
        $meta['route'] = $route;
    }

    // Nur Punkte nach dem letzten gespeicherten - schickt die App nach einem Netzproblem
    // dieselben noch einmal, landen sie nicht doppelt in der Datei.
    $lines = '';
    $stored = 0;
    $lastT = (int)$meta['lastT'];
    $pts = is_array($in['points'] ?? null) ? array_filter(array_slice($in['points'], 0, 2000), 'is_array') : [];
    usort($pts, fn($a, $b) => (int)($a['t'] ?? 0) <=> (int)($b['t'] ?? 0));
    foreach ($pts as $p) {
        if (!is_numeric($p['lat'] ?? null) || !is_numeric($p['lon'] ?? null) || !is_numeric($p['t'] ?? null)) {
            continue;
        }
        $t = (int)$p['t'];
        $lat = (float)$p['lat'];
        $lon = (float)$p['lon'];
        if ($t <= $lastT || abs($lat) > 90 || abs($lon) > 180 || live_in_privacy_zone($c, $lat, $lon)) {
            continue;
        }
        $ele = is_numeric($p['ele'] ?? null) ? round((float)$p['ele'], 1) : '';
        $hr = is_numeric($p['hr'] ?? null) ? (int)$p['hr'] : '';
        $lines .= sprintf("%d,%.6F,%.6F,%s,%s\n", $t, $lat, $lon, $ele, $hr);
        $lastT = $t;
        $stored++;
    }
    if ($lines !== '') {
        fseek($lock, 0, SEEK_END);
        fwrite($lock, $lines);
        fflush($lock);
    }

    $meta = array_merge($meta, [
        'name' => $name,
        'state' => $state,
        'updated' => (int)(microtime(true) * 1000),
        'stats' => $stats,
        'lastT' => $lastT,
        'count' => (int)$meta['count'] + $stored,
    ]);
    live_write_meta($metaFile, $meta);
    flock($lock, LOCK_UN);
    fclose($lock);

    // Ab und zu aufraeumen statt per Cron.
    if (random_int(1, 20) === 1) {
        live_cleanup($dir, $c);
    }
    live_json(['ok' => true, 'stored' => $stored, 'view' => live_view_url($session)]);
}

// --- GET: Live-Seite fragt nach ---

$meta = live_session_by_key($dir, $c, (string)($_GET['k'] ?? ''), 'view');
if ($meta === null) {
    live_json(['ok' => false, 'error' => 'Link ungültig oder abgelaufen.'], 403);
}

// Auch aufraeumen, wenn laengere Zeit keine Fahrt hochgeladen wird.
if (random_int(1, 50) === 1) {
    live_cleanup($dir, $c);
}

if (($meta['rideId'] ?? null) === null) {
    live_json(['ok' => true, 'ride' => null]);
}
$csvFile = live_session_file($dir, (string)$meta['id'], 'csv');

$since = max(0, (int)($_GET['since'] ?? 0));
$points = [];
$showHr = (bool)$c['show_heart_rate'];
$f = @fopen($csvFile, 'r');
if ($f !== false) {
    flock($f, LOCK_SH);
    $n = 0;
    while (($line = fgets($f)) !== false) {
        $n++;
        if ($n <= $since || !str_ends_with($line, "\n")) {
            continue;
        }
        $v = explode(',', rtrim($line, "\n"));
        if (count($v) < 5) {
            continue;
        }
        $points[] = [
            $n, (int)$v[0], (float)$v[1], (float)$v[2],
            $v[3] === '' ? null : (float)$v[3],
            $showHr && $v[4] !== '' ? (int)$v[4] : null,
        ];
        if (count($points) >= 20000) {
            break;
        }
    }
    flock($f, LOCK_UN);
    fclose($f);
}

$stats = is_array($meta['stats'] ?? null) ? $meta['stats'] : [];
if (!$showHr) {
    unset($stats['hr'], $stats['avgHr']);
}

live_json([
    'ok' => true,
    'ride' => (string)$meta['rideId'],
    'name' => (string)($meta['name'] ?? ''),
    'state' => (string)($meta['state'] ?? 'riding'),
    'updated' => (int)($meta['updated'] ?? 0),
    'now' => (int)(microtime(true) * 1000),
    'stats' => $stats,
    'points' => $points,
    // Die Route nur beim ersten Abruf - sie aendert sich waehrend der Fahrt nicht.
    'route' => $since === 0 ? ($meta['route'] ?? null) : null,
]);
