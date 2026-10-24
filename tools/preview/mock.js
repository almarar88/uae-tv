// Mock of the Android JS bridge for desktop preview (tools/preview/server.py). Not shipped in the APK.
(function () {
  const prefs = JSON.parse(localStorage.__prefs || '{}');
  const sx = (url) => { const x = new XMLHttpRequest(); x.open('GET', url, false); x.send(); return x.responseText; };
  const reply = (id, ok, p) => setTimeout(() => window.__cb(id, ok, p), 0);
  const icon = (t, c) => { const cv = document.createElement('canvas'); cv.width = cv.height = 160; const g = cv.getContext('2d'); g.fillStyle = c; g.beginPath(); g.roundRect(8, 8, 144, 144, 36); g.fill(); g.fillStyle = '#fff'; g.font = 'bold 64px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(t, 80, 84); return cv.toDataURL(); };
  const banner = (t, c) => { const cv = document.createElement('canvas'); cv.width = 320; cv.height = 180; const g = cv.getContext('2d'); const gr = g.createLinearGradient(0, 0, 320, 180); gr.addColorStop(0, c); gr.addColorStop(1, '#111'); g.fillStyle = gr; g.fillRect(0, 0, 320, 180); g.fillStyle = '#fff'; g.font = 'bold 44px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(t, 160, 92); return cv.toDataURL(); };
  // Placeholder app entries for the preview only (a real TV lists its installed apps).
  // SIMULATED device layer for the desktop preview only: generic wordmarks drawn in brand-like colours.
  // No third-party logo files are bundled; on a TV the real artwork comes from each app via PackageManager.
  // kind: b = app ships a TV banner, a = adaptive icon (bg + fg), l = legacy square icon
  // kind: p = no usable banner → the Play-icon pipeline runs (real Play icons are fetched live by the preview server, never stored);
  //       a = adaptive icon only (no Play listing), l = legacy icon only, s = system settings
  const APPS = [['YouTube', 'com.google.android.youtube.tv', 'Y', '#ff0000', 'p', '#fff'], ['Netflix', 'com.netflix.ninja', 'N', '#e50914', 'p', '#000'],
    ['Prime Video', 'com.amazon.amazonvideo.livingroom', 'P', '#1a98ff', 'p', '#0f1a24'], ['شاهد', 'net.mbc.shahid.tv', 'ش', '#22c55e', 'p', '#0b0b0b'],
    ['OSN+', 'com.osn.go', 'O', '#ffffff', 'p', '#111'], ['Disney+', 'com.disney.disneyplus', 'D', '#ffffff', 'p', '#0b1b4a'],
    ['Spotify', 'com.spotify.tv.android', 'S', '#1ed760', 'p', '#121212'], ['أنغامي', 'com.anghami', 'A', '#ffffff', 'p', '#7b2ff7'],
    ['STARZPLAY', 'com.parsifal.starz', 'S', '#ffffff', 'p', '#1f1f1f'], ['beIN SPORTS', 'com.beinsports.andcontent', 'b', '#ffffff', 'p', '#5b2a86'],
    ['Liwaiptv', 'com.liwaiptv.app', 'Liwa', '#E8BE60', 'a', '#1a140c'], ['الإعدادات', 'com.android.tv.settings', '⚙', '#9aa0a6', 's', '#3c4043'],
    ['Chrome', 'com.android.chrome', 'C', '#fbbc04', 'p', '#fff'], ['الملفات', 'com.google.android.apps.nbu.files', 'F', '#7e57c2', 'p', '#fff'], ['Downloader', 'com.esaba.downloader', 'D', '#f57c00', 'p', '#fff']];
  const wordmark = (t, fg, bg, w, h, size) => { const cv = document.createElement('canvas'); cv.width = w; cv.height = h; const g = cv.getContext('2d'); const gr = g.createLinearGradient(0, 0, w, h); gr.addColorStop(0, bg); gr.addColorStop(1, '#000'); g.fillStyle = gr; g.fillRect(0, 0, w, h); g.fillStyle = fg; g.font = `800 ${size}px Kufi, Helvetica, sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(t, w / 2, h / 2 + 4); return cv.toDataURL(); };
  window.Android = {
    prefs: () => JSON.stringify(prefs), setPref: (k, v) => { prefs[k] = v; localStorage.__prefs = JSON.stringify(prefs); }, version: () => '1.3.0',
    cities: () => JSON.stringify([['abudhabi', 'أبوظبي', 24.4539, 54.3773], ['alain', 'العين', 24.2075, 55.7447], ['dubai', 'دبي', 25.2048, 55.2708], ['sharjah', 'الشارقة', 25.3463, 55.4209], ['ajman', 'عجمان', 25.4052, 55.5136], ['uaq', 'أم القيوين', 25.5647, 55.5552], ['rak', 'رأس الخيمة', 25.8007, 55.9762], ['fujairah', 'الفجيرة', 25.1288, 56.3265]].map(([id, ar, lat, lng]) => ({ id, ar, lat, lng }))),
    prayer: (c, y, m, d) => sx(`/__prayer?city=${c}&y=${y}&m=${m}&d=${d}`),
    hijri: (t) => { const p = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura-nu-latn', { timeZone: 'Asia/Dubai', day: 'numeric', month: 'numeric', year: 'numeric' }).formatToParts(new Date(t)); const g = k => +p.find(x => x.type === k).value.replace(/\D/g, ''); return JSON.stringify({ d: g('day'), m: g('month'), y: g('year') }); },
    get: (id, url) => fetch('/__get?url=' + encodeURIComponent(url)).then(r => r.ok ? r.text().then(t => reply(id, true, t)) : reply(id, false, 'HTTP ' + r.status)),
    apps: (id) => reply(id, true, JSON.stringify(APPS.map(([label, pkg, t, fg, k, bg]) => ({ label, pkg, tv: true, icon: icon(t.slice(0, 1), k === 'l' ? fg : bg), banner: k === 'b' ? wordmark(t, fg, bg, 480, 270, t.length > 6 ? 58 : 76) : '', art: k === 'a' ? wordmark(t, fg, bg, 480, 270, 70) : '' })))),
    usage: () => JSON.stringify({ 'com.google.android.youtube.tv': { total: 9 }, 'net.mbc.shahid.tv': { total: 6 } }), launch: () => true,
    playIcon: (id, pkg) => fetch('/__playicon?pkg=' + pkg.replace(/\.(tv|androidtv|livingroom)$/, '') ).then(r => r.ok ? r.blob() : (pkg.endsWith('.livingroom') || pkg.endsWith('.tv') ? fetch('/__playicon?pkg=' + pkg).then(r2 => r2.ok ? r2.blob() : Promise.reject('no listing')) : Promise.reject('no listing'))).then(b => new Promise(res => { const fr = new FileReader(); fr.onload = () => res(fr.result); fr.readAsDataURL(b); })).then(u => reply(id, true, u), e => reply(id, false, String(e))),
    sura: (id, n) => fetch('/__sura?n=' + n).then(r => r.text()).then(t => reply(id, true, t)),
    aiInfo: () => JSON.stringify({ hasKey: true, provider: 'Google Gemini', model: 'gemini-flash-lite-latest', override: false }),
    ai: (id, system, msgs) => fetch('/__ai', { method: 'POST', body: JSON.stringify({ system, messages: JSON.parse(msgs) }) }).then(r => r.json()).then(j => reply(id, j.ok, j.text)),
    setAiKey: () => { }, hasVoice: () => false, listen: () => { }, speak: () => { }, mediaPause: () => { }, stopSpeak: () => { },
    checkUpdate: (id) => fetch('/__get?url=' + encodeURIComponent('https://api.github.com/repos/almarar88/uae-tv/releases/latest')).then(r => r.ok ? r.json().then(o => reply(id, true, JSON.stringify({ latest: o.tag_name.replace(/^v/, ''), current: '1.0.0', newer: false }))) : reply(id, false, 'HTTP ' + r.status)),
    iptvHas: () => true, liwaInstalled: () => true, iptvSet: () => { }, iptvPlay: () => true,
    iptvApi: (id, action, extra) => fetch('/__get?url=' + encodeURIComponent(`http://127.0.0.1:8787/player_api.php?username=demo&password=demo&action=${action}${extra ? '&' + extra : ''}`)).then(r => r.text()).then(t => reply(id, true, t.replace(/"\/img\//g, '"http://127.0.0.1:8787/img/').replace(/\["\/img\//g, '["http://127.0.0.1:8787/img/'))),
    installUpdate: (id) => reply(id, false, 'preview'), testAdhan: () => { }, openDreamSettings: () => { }, openSystemSettings: () => { },
  };
})();
