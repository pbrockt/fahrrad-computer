<?php
// Fahrrad-Computer Live-Link - Schnittstelle.
//
// POST (die App):   Kopfzeile "Authorization: Bearer <upload_token>" (oder "X-Live-Token: ...")
//   {"ping": true}                                 -> {"ok":true,"view":"<Link fuer Zuschauer>"}
//   {"ride":"2026-10-28_08-30","name":"Phillipp","state":"riding|paused|finished",
//    "stats":{...}, "points":[{"t":ms,"lat":..,"lon":..,"ele":..,"hr":..}], "route":[[lat,lon],...]}
//                                                  -> {"ok":true,"stored":<Anzahl>,"view":"..."}
// GET (die Live-Seite):  api.php?k=<view_key>&since=<letzte Punkt-Id>
//                                                  -> neueste Fahrt mit neuen Punkten

declare(strict_types=1);
require __DIR__ . '/lib.php';

$c = live_config();
if (!live_configured($c)) {
    live_json(['ok' => false, 'error' => 'Server noch nicht eingerichtet (config.php fehlt oder enthält Platzhalter).'], 503);
}

try {
    $db = live_db($c);
} catch (Throwable $e) {
    live_json(['ok' => false, 'error' => 'Datenbank: ' . $e->getMessage()], 500);
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method === 'POST') {
    // Manche Server reichen "Authorization" nicht an PHP weiter - daher auch X-Live-Token.
    $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    $token = str_starts_with($auth, 'Bearer ') ? substr($auth, 7) : ($_SERVER['HTTP_X_LIVE_TOKEN'] ?? '');
    if (!hash_equals((string)$c['upload_token'], (string)$token)) {
        live_json(['ok' => false, 'error' => 'Falscher Upload-Token.'], 401);
    }
    $raw = file_get_contents('php://input', false, null, 0, 2_000_000);
    $in = json_decode((string)$raw, true);
    if (!is_array($in)) {
        live_json(['ok' => false, 'error' => 'Kein gültiges JSON.'], 400);
    }
    if (!empty($in['ping'])) {
        live_json(['ok' => true, 'view' => live_view_url($c), 'version' => LIVE_VERSION]);
    }

    $ride = (string)($in['ride'] ?? '');
    if (!preg_match('/^[A-Za-z0-9_.:-]{1,40}$/', $ride)) {
        live_json(['ok' => false, 'error' => 'Ungültige Fahrt-Id.'], 400);
    }
    $state = in_array($in['state'] ?? '', ['riding', 'paused', 'finished'], true) ? $in['state'] : 'riding';
    $name = trim((string)($in['name'] ?? ''));
    $name = function_exists('mb_substr') ? mb_substr($name, 0, 40) : substr($name, 0, 40);
    $stats = is_array($in['stats'] ?? null) ? $in['stats'] : [];
    $now = (int)(microtime(true) * 1000);

    $db->beginTransaction();
    $db->prepare('INSERT INTO rides (id, name, state, updated, stats) VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET name = excluded.name, state = excluded.state,
        updated = excluded.updated, stats = excluded.stats')
        ->execute([$ride, $name, $state, $now, json_encode($stats, JSON_UNESCAPED_UNICODE)]);

    // Geplante Route nur einmal schicken lassen (beim Start); hoechstens 3000 Punkte.
    if (is_array($in['route'] ?? null)) {
        $route = [];
        foreach (array_slice($in['route'], 0, 3000) as $p) {
            if (is_array($p) && isset($p[0], $p[1]) && is_numeric($p[0]) && is_numeric($p[1])) {
                $route[] = [round((float)$p[0], 5), round((float)$p[1], 5)];
            }
        }
        $db->prepare('UPDATE rides SET route = ? WHERE id = ?')->execute([json_encode($route), $ride]);
    }

    $stored = 0;
    $insert = $db->prepare('INSERT OR IGNORE INTO points (ride, t, lat, lon, ele, hr) VALUES (?, ?, ?, ?, ?, ?)');
    foreach (array_slice(is_array($in['points'] ?? null) ? $in['points'] : [], 0, 2000) as $p) {
        if (!is_array($p) || !is_numeric($p['lat'] ?? null) || !is_numeric($p['lon'] ?? null) || !is_numeric($p['t'] ?? null)) {
            continue;
        }
        $lat = (float)$p['lat'];
        $lon = (float)$p['lon'];
        if (abs($lat) > 90 || abs($lon) > 180 || live_in_privacy_zone($c, $lat, $lon)) {
            continue;
        }
        $insert->execute([
            $ride, (int)$p['t'], $lat, $lon,
            is_numeric($p['ele'] ?? null) ? (float)$p['ele'] : null,
            is_numeric($p['hr'] ?? null) ? (int)$p['hr'] : null,
        ]);
        $stored += $insert->rowCount();
    }
    $db->commit();

    // Ab und zu aufraeumen statt per Cron.
    if (random_int(1, 20) === 1) {
        live_cleanup($db, $c);
    }
    live_json(['ok' => true, 'stored' => $stored, 'view' => live_view_url($c)]);
}

// --- GET: Live-Seite fragt nach ---

if (!hash_equals((string)$c['view_key'], (string)($_GET['k'] ?? ''))) {
    live_json(['ok' => false, 'error' => 'Kein Zugriff.'], 403);
}

$ride = $db->query('SELECT * FROM rides ORDER BY updated DESC LIMIT 1')->fetch();
if (!$ride) {
    live_json(['ok' => true, 'ride' => null]);
}

$since = max(0, (int)($_GET['since'] ?? 0));
$q = $db->prepare('SELECT id, t, lat, lon, ele, hr FROM points WHERE ride = ? AND id > ? ORDER BY t LIMIT 20000');
$q->execute([$ride['id'], $since]);
$points = [];
$showHr = (bool)$c['show_heart_rate'];
foreach ($q as $p) {
    $points[] = [
        (int)$p['id'], (int)$p['t'], (float)$p['lat'], (float)$p['lon'],
        $p['ele'] === null ? null : (float)$p['ele'],
        $showHr && $p['hr'] !== null ? (int)$p['hr'] : null,
    ];
}

$stats = json_decode($ride['stats'], true) ?: [];
if (!$showHr) {
    unset($stats['hr'], $stats['avgHr']);
}

live_json([
    'ok' => true,
    'ride' => $ride['id'],
    'name' => $ride['name'],
    'state' => $ride['state'],
    'updated' => (int)$ride['updated'],
    'now' => (int)(microtime(true) * 1000),
    'stats' => $stats,
    'points' => $points,
    // Die Route nur beim ersten Abruf - sie aendert sich waehrend der Fahrt nicht.
    'route' => $since === 0 && $ride['route'] ? json_decode($ride['route'], true) : null,
]);
