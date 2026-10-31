<?php
// Fahrrad-Computer Live-Link - Seite fuer die Zuschauer: https://dein-server/live/?k=<view_key>

declare(strict_types=1);
require __DIR__ . '/lib.php';

$c = live_config();
header('Cache-Control: no-store');
header('Referrer-Policy: no-referrer');
header('X-Robots-Tag: noindex, nofollow');

if (!live_configured($c)) {
    // Erste Einrichtung: Hinweis und zwei frische Zufallswerte zum Kopieren.
    $a = bin2hex(random_bytes(16));
    $b = bin2hex(random_bytes(16));
    ?><!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Live-Link einrichten</title>
<style>body{font:16px/1.5 system-ui,sans-serif;max-width:640px;margin:24px auto;padding:0 16px}code{background:#eee;padding:2px 4px;word-break:break-all}</style>
<h1>Live-Link einrichten</h1>
<p>Es fehlt noch <code>config.php</code> (oder sie enthält Platzhalter).</p>
<ol>
<li><code>config.sample.php</code> nach <code>config.php</code> kopieren.</li>
<li>Diese zwei frischen Zufallswerte eintragen:<br>
upload_token: <code><?= $a ?></code><br>view_key: <code><?= $b ?></code></li>
<li>Seite neu laden.</li>
</ol>
<?php live_checks_html(); ?>
</html><?php
    exit;
}

if (!hash_equals((string)$c['view_key'], (string)($_GET['k'] ?? ''))) {
    http_response_code(403);
    echo '<!doctype html><meta charset="utf-8"><title>Kein Zugriff</title><p style="font-family:sans-serif">Kein Zugriff – bitte den vollständigen Link verwenden.</p>';
    exit;
}

// Datenordner gleich hier pruefen - sonst sieht man auf der Seite nur "keine Verbindung".
try {
    live_dir($c);
} catch (Throwable $e) {
    http_response_code(500);
    ?><!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Live-Link: Speicher</title>
<style>body{font:16px/1.5 system-ui,sans-serif;max-width:640px;margin:24px auto;padding:0 16px}code{background:#eee;padding:2px 4px;word-break:break-all}</style>
<h1>Daten lassen sich nicht speichern</h1>
<p><code><?= htmlspecialchars($e->getMessage()) ?></code></p>
<?php live_checks_html($c); ?>
</html><?php
    exit;
}
$title = htmlspecialchars((string)$c['title']);
?><!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<title><?= $title ?></title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" crossorigin="">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@500;600&display=swap" rel="stylesheet">
<style>
  /* Wie die App: schmale Schrift, abgerundete Kacheln, farbige Werte, Petrol als Hervorhebung. */
  :root { --bg:#e9edf1; --card:#fff; --text:#0d1014; --dim:#5a626c; --line:#cdd3da; --red:#00796b; --speed:#0b7bd0; --dist:#2b8a3e; --alt:#6a4fd8; --hr:#d7141a; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#0a0c0f; --card:#16191e; --text:#f4f6f8; --dim:#969ca5; --line:#2e333a; --red:#14a394; --speed:#2e9bf0; --dist:#3dbb5c; --alt:#a48bf5; --hr:#ff4d4d; }
  }
  * { box-sizing:border-box; }
  html,body { margin:0; height:100%; background:var(--bg); color:var(--text); font-family:"Barlow Condensed",system-ui,sans-serif; }
  body { display:flex; flex-direction:column; }
  header { display:flex; align-items:center; gap:10px; padding:10px 14px; }
  header h1 { margin:0; font-size:22px; font-weight:600; flex:1; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
  .badge { color:#fff; font-weight:600; font-size:16px; padding:2px 10px; border-radius:999px; }
  .badge.live { background:var(--red); animation:pulse 1.6s infinite; }
  .badge.paused { background:#f08a00; }
  .badge.finished { background:#2e9e44; }
  .badge.stale, .badge.none { background:#8e8e93; }
  @keyframes pulse { 50% { opacity:.55; } }
  #map { flex:1; min-height:240px; }
  .grid { display:grid; grid-template-columns:repeat(3,1fr); gap:6px; padding:6px; background:var(--bg); }
  .tile { background:var(--card); border-radius:14px; text-align:center; padding:8px 10px 10px; }
  .tile .l { font-size:15px; font-weight:600; text-align:left; }
  .tile .l small { opacity:.6; font-size:12px; }
  .tile .v { font-size:clamp(30px,9vw,52px); font-weight:600; line-height:1.05; }
  footer { color:var(--dim); font-size:14px; text-align:center; padding:6px 10px 10px; font-family:system-ui,sans-serif; }
  .me { width:22px; height:22px; border-radius:50%; background:var(--red); border:3px solid #fff; box-shadow:0 0 0 2px var(--red); }
</style>
</head>
<body>
<header><h1 id="title"><?= $title ?></h1><span id="badge" class="badge none">–</span></header>
<div id="map"></div>
<div class="grid">
  <div class="tile" style="color:var(--dist)"><div class="l">Distanz <small>km</small></div><div class="v" id="dist">–</div></div>
  <div class="tile"><div class="l">Fahrzeit</div><div class="v" id="time">–</div></div>
  <div class="tile" style="color:var(--speed)"><div class="l">Tempo <small>km/h</small></div><div class="v" id="speed">–</div></div>
  <div class="tile" style="color:var(--speed)"><div class="l">Ø Tempo <small>km/h</small></div><div class="v" id="avg">–</div></div>
  <div class="tile" style="color:var(--alt)"><div class="l">Höhenmeter <small>m</small></div><div class="v" id="asc">–</div></div>
  <div class="tile" style="color:var(--dist)" id="restTile"><div class="l">Rest <small>km</small></div><div class="v" id="rest">–</div></div>
  <div class="tile" style="color:var(--hr);display:none" id="hrTile"><div class="l">Puls <small>bpm</small></div><div class="v" id="hr">–</div></div>
</div>
<footer id="foot">Lädt …</footer>

<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" crossorigin=""></script>
<script>
const KEY = <?= json_encode((string)$_GET['k']) ?>;
const TITLE = <?= json_encode((string)$c['title'], JSON_UNESCAPED_UNICODE) ?>;
const POLL_MS = 15000;
// Gilt als "nicht mehr live", wenn so lange nichts kam (Funkloch, Akku leer).
const STALE_MS = 5 * 60 * 1000;

const map = L.map('map', { zoomControl: true });
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19, attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>-Mitwirkende',
}).addTo(map);
map.setView([51.1, 10.4], 6);

const css = n => getComputedStyle(document.documentElement).getPropertyValue(n).trim();
const routeLine = L.polyline([], { color: '#888', weight: 5, opacity: 0.6, dashArray: '8 8' }).addTo(map);
const trackLine = L.polyline([], { color: css('--red'), weight: 5 }).addTo(map);
const marker = L.marker([0, 0], { icon: L.divIcon({ className: '', html: '<div class="me"></div>', iconSize: [22, 22], iconAnchor: [11, 11] }) });

let rideId = null, lastId = 0, points = [], fitted = false, userMoved = false;
// Wer die Karte selbst verschiebt, dem folgt sie nicht mehr.
map.on('dragstart', () => { userMoved = true; });

const de = (v, d = 1) => (v == null || isNaN(v)) ? '–' : Number(v).toLocaleString('de-DE', { minimumFractionDigits: d, maximumFractionDigits: d });
function duration(ms) {
  if (ms == null) return '–';
  const s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), sec = s % 60;
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}` : `${m}:${String(sec).padStart(2, '0')}`;
}
function ago(ms) {
  const m = Math.round(ms / 60000);
  return m < 1 ? 'gerade eben' : m === 1 ? 'vor 1 Minute' : m < 60 ? `vor ${m} Minuten` : `vor ${Math.round(m / 60)} Std.`;
}

async function poll() {
  try {
    const r = await fetch(`api.php?k=${encodeURIComponent(KEY)}&since=${lastId}`, { cache: 'no-store' });
    const d = await r.json();
    if (!d.ok) { document.getElementById('foot').textContent = 'Server: ' + (d.error || 'Fehler'); return; }
    render(d);
  } catch (e) {
    document.getElementById('foot').textContent = 'Keine Verbindung zum Server – neuer Versuch gleich.';
  }
}

function render(d) {
  const badge = document.getElementById('badge');
  if (!d.ride) {
    badge.className = 'badge none'; badge.textContent = 'Keine Fahrt';
    document.getElementById('foot').textContent = 'Gerade ist keine Fahrt unterwegs.';
    return;
  }
  // Neue Fahrt begonnen: alles zuruecksetzen.
  if (d.ride !== rideId) {
    const fresh = rideId === null;
    rideId = d.ride; points = []; fitted = false; routeLine.setLatLngs([]);
    // War schon eine andere Fahrt geladen, fehlen jetzt Route und Anfang - komplett neu holen.
    if (!fresh && lastId > 0) { lastId = 0; poll(); return; }
    lastId = 0;
  }
  if (d.route) routeLine.setLatLngs(d.route);
  for (const p of d.points) { points.push(p); lastId = Math.max(lastId, p[0]); }
  points.sort((a, b) => a[1] - b[1]);
  const latlngs = points.map(p => [p[2], p[3]]);
  trackLine.setLatLngs(latlngs);
  if (latlngs.length) {
    const last = latlngs[latlngs.length - 1];
    marker.setLatLng(last).addTo(map);
    if (!fitted) {
      const b = L.latLngBounds(latlngs);
      if (d.route && d.route.length) b.extend(L.latLngBounds(d.route));
      map.fitBounds(b, { padding: [30, 30], maxZoom: 15 });
      fitted = true;
    } else if (!userMoved) {
      map.panTo(last);
    }
  }

  const age = d.now - d.updated;
  const s = d.stats || {};
  const stale = d.state !== 'finished' && age > STALE_MS;
  badge.className = 'badge ' + (stale ? 'stale' : d.state === 'riding' ? 'live' : d.state);
  badge.textContent = stale ? 'Kein Signal' : d.state === 'riding' ? 'Live' : d.state === 'paused' ? 'Pause' : 'Angekommen';
  document.getElementById('title').textContent = d.name ? `${d.name} · ${TITLE}` : TITLE;

  document.getElementById('dist').textContent = de(s.distanceM / 1000, 2);
  document.getElementById('time').textContent = duration(s.movingMs);
  document.getElementById('speed').textContent = d.state === 'riding' && !stale ? de(s.speedKmh) : '–';
  document.getElementById('avg').textContent = de(s.avgKmh);
  document.getElementById('asc').textContent = de(s.ascentM, 0);
  const rest = document.getElementById('restTile');
  if (s.remainingM != null) { rest.style.display = ''; document.getElementById('rest').textContent = de(s.remainingM / 1000, 1); }
  else rest.style.display = 'none';
  if (s.hr != null) { document.getElementById('hrTile').style.display = ''; document.getElementById('hr').textContent = s.hr; }

  const battery = s.battery != null ? ` · Handy-Akku ${s.battery} %` : '';
  document.getElementById('foot').textContent = `Letzte Meldung ${ago(age)}${battery}`;
}

poll();
setInterval(poll, POLL_MS);
</script>
</body>
</html>
