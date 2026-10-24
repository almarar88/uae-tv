// Mock of the Android JS bridge for desktop preview (tools/preview/server.py). Not shipped in the APK.
(function () {
  const prefs = JSON.parse(localStorage.__prefs || '{}');
  const sx = (url) => { const x = new XMLHttpRequest(); x.open('GET', url, false); x.send(); return x.responseText; };
  const reply = (id, ok, p) => setTimeout(() => window.__cb(id, ok, p), 0);
  const icon = (t, c) => { const cv = document.createElement('canvas'); cv.width = cv.height = 160; const g = cv.getContext('2d'); g.fillStyle = c; g.beginPath(); g.roundRect(8, 8, 144, 144, 36); g.fill(); g.fillStyle = '#fff'; g.font = 'bold 64px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(t, 80, 84); return cv.toDataURL(); };
  // Placeholder app entries for the preview only (a real TV lists its installed apps).
  const APPS = [['YouTube', 'com.google.android.youtube.tv', '▶', '#c4302b'], ['Netflix', 'com.netflix.ninja', 'N', '#b20710'], ['شاهد', 'net.mbc.shahid.tv', 'ش', '#1f8a70'],
    ['Prime Video', 'com.amazon.amazonvideo.livingroom', 'P', '#1a98ff'], ['Spotify', 'com.spotify.tv.android', '♪', '#1db954'], ['Play Store', 'com.android.vending', '▷', '#4285f4'],
    ['الإعدادات', 'com.android.tv.settings', '⚙', '#5f6368'], ['Chrome', 'com.android.chrome', 'C', '#fbbc04'], ['الملفات', 'com.android.documentsui', '▤', '#7e57c2']];
  window.Android = {
    prefs: () => JSON.stringify(prefs), setPref: (k, v) => { prefs[k] = v; localStorage.__prefs = JSON.stringify(prefs); }, version: () => '1.0.0',
    cities: () => JSON.stringify([['abudhabi', 'أبوظبي', 24.4539, 54.3773], ['alain', 'العين', 24.2075, 55.7447], ['dubai', 'دبي', 25.2048, 55.2708], ['sharjah', 'الشارقة', 25.3463, 55.4209], ['ajman', 'عجمان', 25.4052, 55.5136], ['uaq', 'أم القيوين', 25.5647, 55.5552], ['rak', 'رأس الخيمة', 25.8007, 55.9762], ['fujairah', 'الفجيرة', 25.1288, 56.3265]].map(([id, ar, lat, lng]) => ({ id, ar, lat, lng }))),
    prayer: (c, y, m, d) => sx(`/__prayer?city=${c}&y=${y}&m=${m}&d=${d}`),
    hijri: (t) => { const p = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura-nu-latn', { timeZone: 'Asia/Dubai', day: 'numeric', month: 'numeric', year: 'numeric' }).formatToParts(new Date(t)); const g = k => +p.find(x => x.type === k).value.replace(/\D/g, ''); return JSON.stringify({ d: g('day'), m: g('month'), y: g('year') }); },
    get: (id, url) => fetch('/__get?url=' + encodeURIComponent(url)).then(r => r.ok ? r.text().then(t => reply(id, true, t)) : reply(id, false, 'HTTP ' + r.status)),
    apps: (id) => reply(id, true, JSON.stringify(APPS.map(([label, pkg, t, c], i) => ({ label, pkg, icon: icon(t, c), banner: '', tv: true })))),
    usage: () => JSON.stringify({ 'com.google.android.youtube.tv': { total: 9 }, 'net.mbc.shahid.tv': { total: 6 } }), launch: () => true,
    sura: (id, n) => fetch('/__sura?n=' + n).then(r => r.text()).then(t => reply(id, true, t)),
    aiInfo: () => JSON.stringify({ hasKey: true, provider: 'Google Gemini', model: 'gemini-flash-lite-latest', override: false }),
    ai: (id, system, msgs) => fetch('/__ai', { method: 'POST', body: JSON.stringify({ system, messages: JSON.parse(msgs) }) }).then(r => r.json()).then(j => reply(id, j.ok, j.text)),
    setAiKey: () => { }, hasVoice: () => false, listen: () => { }, speak: () => { }, stopSpeak: () => { },
    checkUpdate: (id) => fetch('/__get?url=' + encodeURIComponent('https://api.github.com/repos/almarar88/uae-tv/releases/latest')).then(r => r.ok ? r.json().then(o => reply(id, true, JSON.stringify({ latest: o.tag_name.replace(/^v/, ''), current: '1.0.0', newer: false }))) : reply(id, false, 'HTTP ' + r.status)),
    installUpdate: (id) => reply(id, false, 'preview'), testAdhan: () => { }, openDreamSettings: () => { }, openSystemSettings: () => { },
  };
})();
