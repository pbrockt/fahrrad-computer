<?php
// Fahrrad-Computer Live-Link
//   https://dein-server/live/            Startseite (mit Passwort): neue Fahrt starten, Links und QR-Code
//   https://dein-server/live/?k=<...>    Live-Karte fuer Zuschauer

declare(strict_types=1);
require __DIR__ . '/lib.php';

$c = live_config();
header('Cache-Control: no-store');
// Nur die Domain mitschicken (nie Pfad oder ?k=...) - OpenStreetMap sperrt Kacheln ganz ohne Referer.
header('Referrer-Policy: strict-origin');
header('X-Robots-Tag: noindex, nofollow');

/** Einfache Seite im Stil der Live-Karte. */
function live_page_start(string $title): void
{
    ?><!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex,nofollow"><title><?= htmlspecialchars($title) ?></title>
<style>
  :root { --bg:#e9edf1; --card:#fff; --text:#0d1014; --dim:#5a626c; --line:#cdd3da; --acc:#00796b; --wa:#25d366; --red:#c62828; }
  @media (prefers-color-scheme: dark) { :root { --bg:#0a0c0f; --card:#16191e; --text:#f4f6f8; --dim:#969ca5; --line:#2e333a; --acc:#14a394; --red:#ef5350; } }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg); color:var(--text); font:17px/1.45 system-ui,sans-serif; }
  main { max-width:620px; margin:0 auto; padding:16px; }
  h1 { font-size:26px; margin:6px 0 14px; }
  h2 { font-size:19px; margin:0 0 8px; }
  .card { background:var(--card); border-radius:14px; padding:16px; margin-bottom:12px; }
  .btn { display:inline-block; border:0; border-radius:12px; padding:12px 16px; font:600 17px system-ui,sans-serif; cursor:pointer;
         background:var(--acc); color:#fff; text-decoration:none; margin:4px 6px 4px 0; }
  .btn.big { display:block; width:100%; text-align:center; padding:18px; font-size:20px; }
  .btn.wa { background:var(--wa); color:#063; }
  .btn.ghost { background:transparent; color:var(--acc); border:2px solid var(--acc); }
  .btn.red { background:transparent; color:var(--red); border:2px solid var(--red); }
  .val { font:15px ui-monospace,monospace; background:var(--bg); border-radius:8px; padding:10px; word-break:break-all; margin:6px 0; }
  .dim { color:var(--dim); font-size:15px; }
  #qr { background:#fff; border-radius:12px; padding:8px; max-width:300px; margin:8px auto; }
  #qr svg { width:100%; height:auto; display:block; }
  input[type=password] { width:100%; font-size:18px; padding:12px; border-radius:10px; border:1px solid var(--line); background:var(--bg); color:var(--text); margin:8px 0; }
  ul.rides { list-style:none; padding:0; margin:0; }
  ul.rides li { border-top:1px solid var(--line); padding:10px 0; display:flex; justify-content:space-between; gap:8px; align-items:center; }
  ul.rides a { color:var(--acc); font-weight:600; text-decoration:none; }
</style></head><body><main><?php
}

function live_page_end(): never
{
    echo '</main></body></html>';
    exit;
}

if (!live_configured($c)) {
    // Erste Einrichtung: Hinweis und ein frisches Passwort-Beispiel.
    live_page_start('Live-Link einrichten');
    ?><h1>Live-Link einrichten</h1>
<div class="card"><p>Es fehlt noch <code>config.php</code> (oder darin steht kein Passwort).</p>
<ol>
<li><code>config.sample.php</code> nach <code>config.php</code> kopieren.</li>
<li>Bei <code>admin_password</code> ein Passwort eintragen (mindestens 8 Zeichen), z. B.:<br>
<span class="val"><?= bin2hex(random_bytes(6)) ?></span></li>
<li>Seite neu laden.</li>
</ol></div>
<div class="card"><?php live_checks_html(); ?></div>
<?php
    live_page_end();
}

// --- Zuschauer: ?k=... ---------------------------------------------------------

if (isset($_GET['k'])) {
    try {
        $dir = live_dir($c);
    } catch (Throwable $e) {
        http_response_code(500);
        live_page_start('Live-Link');
        echo '<h1>Daten lassen sich nicht speichern</h1><div class="card"><p>' . htmlspecialchars($e->getMessage()) . '</p></div>';
        live_page_end();
    }
    if (live_session_by_key($dir, $c, (string)$_GET['k'], 'view') === null) {
        http_response_code(404);
        live_page_start('Live-Fahrt');
        echo '<h1>Diese Fahrt gibt es nicht (mehr)</h1><div class="card"><p>Der Link ist falsch oder die Fahrt ist älter als '
            . (int)$c['keep_days'] . ' Tage und wurde gelöscht.</p></div>';
        live_page_end();
    }
    live_viewer($c);
    exit;
}

// --- Startseite mit Passwort ---------------------------------------------------

try {
    $dir = live_dir($c);
} catch (Throwable $e) {
    http_response_code(500);
    live_page_start('Live-Link');
    echo '<h1>Daten lassen sich nicht speichern</h1><div class="card"><p>' . htmlspecialchars($e->getMessage()) . '</p>';
    live_checks_html($c);
    echo '</div>';
    live_page_end();
}

$self = strtok($_SERVER['REQUEST_URI'] ?? './', '?') ?: './';
$action = (string)($_POST['action'] ?? '');

if ($action === 'login') {
    if (hash_equals((string)$c['admin_password'], (string)($_POST['password'] ?? ''))) {
        live_set_admin_cookie($c, true);
        header('Location: ' . $self, true, 303);
        exit;
    }
    sleep(2); // Raten bremsen
    $loginError = true;
}

if (!live_is_admin($c)) {
    live_page_start('Live-Fahrten');
    ?><h1>Live-Fahrten</h1>
<div class="card"><form method="post">
<h2>Anmelden</h2>
<p class="dim">Mit dem Passwort aus <code>config.php</code> (<code>admin_password</code>). Der Browser merkt es sich.</p>
<?php if (!empty($loginError)) echo '<p style="color:var(--red)">Passwort falsch.</p>'; ?>
<input type="password" name="password" autocomplete="current-password" autofocus required>
<input type="hidden" name="action" value="login">
<button class="btn big">Anmelden</button>
</form></div><?php
    live_page_end();
}

if ($action === 'new') {
    $s = live_session_create($dir);
    header('Location: ' . $self . '?s=' . $s['id'], true, 303);
    exit;
}
if ($action === 'delete') {
    live_session_delete($dir, (string)($_POST['id'] ?? ''));
    header('Location: ' . $self, true, 303);
    exit;
}
if ($action === 'logout') {
    live_set_admin_cookie($c, false);
    header('Location: ' . $self, true, 303);
    exit;
}

if (random_int(1, 10) === 1) {
    live_cleanup($dir, $c);
}

$fmtTime = fn(int $ms) => $ms > 0 ? date('d.m. H:i', intdiv($ms, 1000)) : '–';
$stateText = fn(array $m) => match ($m['state'] ?? 'new') {
    'riding' => 'unterwegs', 'paused' => 'Pause', 'finished' => 'angekommen', default => 'noch nicht gestartet',
};

live_page_start('Live-Fahrten');
echo '<h1>Live-Fahrten</h1>';

$current = isset($_GET['s']) ? live_session_load($dir, (string)$_GET['s']) : null;
if ($current !== null) {
    $view = live_view_url($current);
    $app = live_app_url($current);
    $upload = (string)$current['upload'];
    $waView = 'https://wa.me/?text=' . rawurlencode("Verfolge meine Fahrt live: $view");
    $waKey = 'https://wa.me/?text=' . rawurlencode("Upload-Key für die Fahrrad-Computer-App:\n$upload\n\nOder direkt übernehmen: $app");
    ?>
<div class="card">
  <h2>1 · Link für Zuschauer</h2>
  <div class="val" id="view"><?= htmlspecialchars($view) ?></div>
  <button class="btn" onclick="copy('view', this)">Kopieren</button>
  <a class="btn wa" href="<?= htmlspecialchars($waView) ?>" target="_blank" rel="noopener">Per WhatsApp teilen</a>
  <a class="btn ghost" href="<?= htmlspecialchars($view) ?>" target="_blank" rel="noopener">Öffnen</a>
</div>
<div class="card">
  <h2>2 · In die App übernehmen</h2>
  <p class="dim">Am PC: in der App <b>Einstellungen → Live-Link → QR-Code scannen</b>.<br>
  Am Handy: auf „In der App übernehmen“ tippen.</p>
  <div id="qr"></div>
  <a class="btn big" href="<?= htmlspecialchars($app) ?>">In der App übernehmen</a>
  <p class="dim" style="margin-top:12px">Oder den Upload-Key von Hand eintragen:</p>
  <div class="val" id="key"><?= htmlspecialchars($upload) ?></div>
  <button class="btn" onclick="copy('key', this)">Kopieren</button>
  <a class="btn wa" href="<?= htmlspecialchars($waKey) ?>" target="_blank" rel="noopener">An mich per WhatsApp</a>
  <p class="dim">Server-Adresse in der App: <b><?= htmlspecialchars(live_base_url()) ?></b></p>
</div>
<div class="card">
  <p class="dim">Erstellt <?= $fmtTime((int)$current['created']) ?> · <?= $stateText($current) ?>
  · wird <?= (int)$c['keep_days'] ?> Tage nach der letzten Meldung gelöscht.</p>
  <form method="post" onsubmit="return confirm('Diese Fahrt mit allen Punkten löschen? Der Link geht dann nicht mehr.')">
    <input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= htmlspecialchars((string)$current['id']) ?>">
    <button class="btn red">Fahrt löschen</button>
  </form>
</div>
<!-- Der QR-Code entsteht im Browser - der Key geht an keinen fremden Dienst. -->
<script src="https://cdn.jsdelivr.net/npm/qrcode-generator@1.4.4/qrcode.js"></script>
<script>
const q = qrcode(0, 'M'); q.addData(<?= json_encode($app, JSON_UNESCAPED_SLASHES) ?>); q.make();
document.getElementById('qr').innerHTML = q.createSvgTag({ cellSize: 6, margin: 2, scalable: true });
function copy(id, btn) {
  const text = document.getElementById(id).textContent;
  const done = () => { const t = btn.textContent; btn.textContent = 'Kopiert ✓'; setTimeout(() => btn.textContent = t, 1500); };
  if (navigator.clipboard) navigator.clipboard.writeText(text).then(done, () => fallback(id, done)); else fallback(id, done);
}
function fallback(id, done) {
  const r = document.createRange(); r.selectNodeContents(document.getElementById(id));
  const sel = getSelection(); sel.removeAllRanges(); sel.addRange(r);
  try { document.execCommand('copy'); done(); } catch (e) {}
}
</script>
<?php
}

?>
<form method="post" class="card">
  <input type="hidden" name="action" value="new">
  <button class="btn big">Neue Fahrt starten</button>
  <p class="dim" style="margin:8px 0 0">Jede Fahrt bekommt einen eigenen Link und Upload-Key.</p>
</form>
<?php
$all = live_sessions($dir);
if ($all) {
    echo '<div class="card"><h2>Fahrten der letzten ' . (int)$c['keep_days'] . ' Tage</h2><ul class="rides">';
    foreach ($all as $m) {
        $label = ($m['name'] ?? '') !== '' ? htmlspecialchars((string)$m['name']) . ' · ' : '';
        echo '<li><span>' . $label . $fmtTime((int)$m['created']) . ' <span class="dim">· ' . $stateText($m) . '</span></span>'
            . '<a href="?s=' . htmlspecialchars((string)$m['id']) . '">Öffnen</a></li>';
    }
    echo '</ul></div>';
}
?>
<form method="post" style="text-align:center"><input type="hidden" name="action" value="logout">
<button class="btn ghost">Abmelden</button></form>
<?php
live_page_end();

/** Die Live-Karte fuer Zuschauer. */
function live_viewer(array $c): void
{
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
    badge.className = 'badge none'; badge.textContent = 'Gleich geht’s los';
    document.getElementById('foot').textContent = 'Die Fahrt hat noch nicht begonnen – die Seite aktualisiert sich von selbst.';
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
<?php
}
