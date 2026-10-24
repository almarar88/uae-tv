/* مجلس — Majlis TV · original code, MIT licensed. */
'use strict';
const A = window.Android || null;
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const IS_DREAM = location.hash === '#ambient';

/* ── async bridge ───────────────────────────────────────── */
let _id = 0; const _p = {};
window.__cb = (id, ok, payload) => { const p = _p[id]; if (!p) return; delete _p[id]; ok ? p.res(payload) : p.rej(new Error(payload)); };
function call(method, ...args) {
  return new Promise((res, rej) => {
    if (!A || !A[method]) return rej(new Error('bridge:' + method));
    const id = 'c' + (++_id); _p[id] = { res, rej };
    A[method](id, ...args);
  });
}
const getJSON = async url => JSON.parse(await call('get', url));

/* ── prefs ──────────────────────────────────────────────── */
let P = {};
function loadPrefs() { try { P = JSON.parse(A.prefs()); } catch { P = {}; } }
const pref = (k, d) => (P[k] ?? d);
function setPref(k, v) { P[k] = v; A && A.setPref && A.setPref(k, String(v)); }

/* ── time & dates (UAE) ─────────────────────────────────── */
const TZ = 'Asia/Dubai';
const HIJRI_M = ['محرم', 'صفر', 'ربيع الأول', 'ربيع الآخر', 'جمادى الأولى', 'جمادى الآخرة', 'رجب', 'شعبان', 'رمضان', 'شوال', 'ذو القعدة', 'ذو الحجة'];
const GREG_M = ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];
const DAYS = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
function uaeParts(t = Date.now()) {
  const d = new Date(t + 4 * 3600e3); // UAE = UTC+4, no DST
  return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), h: d.getUTCHours(), mi: d.getUTCMinutes(), wd: d.getUTCDay() };
}
const pad = n => String(n).padStart(2, '0');
function fmtTime(t, withAmPm = true) {
  const p = uaeParts(t);
  if (pref('clock', '12') === '24') return pad(p.h) + ':' + pad(p.mi);
  const h = p.h % 12 || 12;
  return h + ':' + pad(p.mi) + (withAmPm ? (p.h < 12 ? ' ص' : ' م') : '');
}
function hijri(t = Date.now()) { try { return JSON.parse(A.hijri(t)); } catch { return null; } }
function dateLines(t = Date.now()) {
  const p = uaeParts(t), h = hijri(t);
  return { greg: `${DAYS[p.wd]} ${p.d} ${GREG_M[p.m - 1]} ${p.y}`, hijri: h ? `${h.d} ${HIJRI_M[h.m - 1]} ${h.y} هـ` : '' };
}

/* ── cities & prayer ────────────────────────────────────── */
let CITIES = [];
const city = () => CITIES.find(c => c.id === pref('city', 'abudhabi')) || CITIES[0];
const PNAMES = ['الفجر', 'الشروق', 'الظهر', 'العصر', 'المغرب', 'العشاء'];
function prayerDay(t = Date.now()) { const p = uaeParts(t); return JSON.parse(A.prayer(city().id, p.y, p.m, p.d)); }
function nextPrayer() {
  const now = Date.now();
  const today = prayerDay(now);
  for (let i = 0; i < 6; i++) if (i !== 1 && today[i] > now) return { i, t: today[i], today };
  const tm = prayerDay(now + 86400e3); return { i: 0, t: tm[0], today };
}
function untilText(ms) {
  const m = Math.max(0, Math.round(ms / 60000)), h = Math.floor(m / 60), r = m % 60;
  if (h && r) return `بعد ${h} س و${r} د`; if (h) return `بعد ${h} ساعة`; return `بعد ${r} دقيقة`;
}

/* ── backgrounds ────────────────────────────────────────── */
let bgIdx = 0, bgFlip = 0;
function photoList() { const f = pref('bg', 'auto'); return f === 'auto' ? PHOTOS : PHOTOS.filter(p => p.file === f).concat([]); }
function showBg(i) {
  const list = photoList(); if (!list.length) return;
  const ph = list[i % list.length];
  const next = $('#bg' + (bgFlip ^ 1)), cur = $('#bg' + bgFlip);
  next.style.backgroundImage = `url('${ph.file}')`;
  next.classList.add('on'); cur.classList.remove('on'); bgFlip ^= 1;
  const c = `📷 ${esc(ph.ar)} — <bdi>${esc(ph.artist)}</bdi> · <bdi>${esc(ph.license)}</bdi> · <bdi>Wikimedia Commons</bdi>`;
  $('#credit').innerHTML = c; $('#amCredit').innerHTML = c;
}
function startBg() { bgIdx = Math.floor(Math.random() * PHOTOS.length); showBg(bgIdx); setInterval(() => showBg(++bgIdx), IS_DREAM ? 45000 : 90000); }

/* ── clock tick ─────────────────────────────────────────── */
function tick() {
  const now = Date.now(), dl = dateLines(now);
  $('#clock').textContent = fmtTime(now, false);
  $('#hijri').textContent = dl.hijri; $('#greg').textContent = dl.greg;
  $('#amClock').textContent = fmtTime(now, false);
  $('#amDates').textContent = dl.hijri + '  •  ' + dl.greg;
  try {
    const np = nextPrayer();
    $('#pCity').textContent = city().ar;
    $('#nextName').textContent = PNAMES[np.i] + ' · ' + fmtTime(np.t);
    $('#nextIn').textContent = untilText(np.t - now);
    $('#amPrayer').textContent = `${PNAMES[np.i]} ${fmtTime(np.t)} — ${untilText(np.t - now)}`;
    $('#pList').innerHTML = np.today.map((t, i) => `<div class="${i === np.i && np.t === t ? 'next' : ''}">${PNAMES[i]}<b>${fmtTime(t, false)}</b></div>`).join('');
  } catch (e) { }
}

/* ── weather (Open-Meteo, CC BY 4.0) ────────────────────── */
const WMO = { 0: ['صحو', '☀'], 1: ['صحو غالباً', '🌤'], 2: ['غائم جزئياً', '⛅'], 3: ['غائم', '☁'], 45: ['ضباب', '🌫'], 48: ['ضباب متجمد', '🌫'], 51: ['رذاذ خفيف', '🌦'], 53: ['رذاذ', '🌦'], 55: ['رذاذ كثيف', '🌧'], 61: ['مطر خفيف', '🌦'], 63: ['مطر', '🌧'], 65: ['مطر غزير', '🌧'], 80: ['زخات مطر', '🌦'], 81: ['زخات مطر', '🌧'], 82: ['زخات غزيرة', '⛈'], 95: ['عاصفة رعدية', '⛈'], 96: ['عاصفة رعدية مع برد', '⛈'], 99: ['عاصفة رعدية مع برد', '⛈'] };
const wmo = c => WMO[c] || ['—', '•'];
let WX = null;
async function loadWeather() {
  const c = city();
  try {
    WX = await getJSON(`https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lng}&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,wind_gusts_10m_max,uv_index_max,precipitation_sum&hourly=visibility&timezone=Asia%2FDubai&forecast_days=4`);
    localStorage.wx = JSON.stringify({ at: Date.now(), city: c.id, WX });
  } catch (e) { try { const s = JSON.parse(localStorage.wx); if (s.city === c.id) WX = s.WX; } catch { } }
  renderWeather(); window.onWeather && onWeather();
}
function renderWeather() {
  const c = city(); $('#wxCity').textContent = c.ar; $('#wCity').textContent = c.ar;
  if (!WX) { $('#wxDesc').textContent = 'الطقس غير متاح'; return; }
  const cu = WX.current, [d, ic] = wmo(cu.weather_code);
  $('#wxTemp').textContent = ic + ' ' + Math.round(cu.temperature_2m) + '°';
  $('#wxDesc').innerHTML = esc(d) + ' · رطوبة <bdi>' + cu.relative_humidity_2m + '%</bdi>';
  $('#wNow').innerHTML = `${ic} ${Math.round(cu.temperature_2m)}° — ${d}<br><small>الإحساس ${Math.round(cu.apparent_temperature)}° · الرطوبة ${cu.relative_humidity_2m}% · الرياح ${Math.round(cu.wind_speed_10m)} كم/س</small>`;
  const dd = WX.daily;
  $('#wDays').innerHTML = dd.time.map((t, i) => { const [ds, di] = wmo(dd.weather_code[i]); const wd = new Date(t + 'T12:00:00Z').getUTCDay(); return `<div class="day">${i ? DAYS[wd] : 'اليوم'}<b>${di} ${Math.round(dd.temperature_2m_max[i])}° / ${Math.round(dd.temperature_2m_min[i])}°</b>${ds}</div>`; }).join('');
}

/* ── football (TheSportsDB free API) ─────────────────────── */
const LEAGUE = 4678; // UAE Pro League on TheSportsDB
const TEAM_AR = { 'Al Ain': 'العين', 'Al Jazira': 'الجزيرة', 'Al Wahda': 'الوحدة', 'Al Wasl': 'الوصل', 'Shabab Al-Ahli Dubai': 'شباب الأهلي', 'Shabab Al-Ahli': 'شباب الأهلي', 'Sharjah': 'الشارقة', 'Al Sharjah': 'الشارقة', 'Al Nasr': 'النصر', 'Al-Nasr': 'النصر', 'Ajman': 'عجمان', 'Baniyas': 'بني ياس', 'Bani Yas': 'بني ياس', 'Khor Fakkan': 'خورفكان', 'Kalba': 'كلباء', 'Ittihad Kalba': 'اتحاد كلباء', 'Al Bataeh': 'البطائح', 'Al Dhafra': 'الظفرة', 'Dibba Al Fujairah': 'دبا الفجيرة', 'Dibba': 'دبا', 'Al Urooba': 'العروبة', 'Hatta': 'حتا', 'Emirates Club': 'الإمارات', 'Al Orooba': 'العروبة' };
const tn = n => TEAM_AR[n] || n;
let FOOT = null;
async function loadFootball() {
  const B = 'https://www.thesportsdb.com/api/v1/json/123';
  try {
    const lg = await getJSON(`${B}/lookupleague.php?id=${LEAGUE}`);
    const season = lg.leagues?.[0]?.strCurrentSeason;
    const p = uaeParts(), today = `${p.y}-${pad(p.m)}-${pad(p.d)}`;
    const [tb, td, nx, ps] = await Promise.all([
      season ? getJSON(`${B}/lookuptable.php?l=${LEAGUE}&s=${season}`).catch(() => null) : null,
      getJSON(`${B}/eventsday.php?d=${today}&l=${LEAGUE}`).catch(() => null),
      getJSON(`${B}/eventsnextleague.php?id=${LEAGUE}`).catch(() => null),
      getJSON(`${B}/eventspastleague.php?id=${LEAGUE}`).catch(() => null)]);
    const onlyLeague = a => (a || []).filter(e => String(e.idLeague) === String(LEAGUE));
    FOOT = { season, table: tb?.table || [], today: onlyLeague(td?.events), next: onlyLeague(nx?.events), past: onlyLeague(ps?.events), at: Date.now() };
    if (!FOOT.table.length && !FOOT.today.length && !FOOT.next.length && !FOOT.past.length) FOOT = null;
  } catch (e) { FOOT = null; }
  renderFootball();
}
function evTime(e) { // strTimestamp is UTC
  if (e.strTimestamp) { const t = Date.parse(e.strTimestamp.replace(' ', 'T') + (e.strTimestamp.endsWith('Z') ? '' : 'Z')); if (!isNaN(t)) return t; }
  return null;
}
function evLine(e, score) {
  const t = evTime(e), when = t ? `${e.dateEvent} · ${fmtTime(t)}` : (e.dateEvent || '');
  const sc = score && e.intHomeScore != null ? ` <b>${e.intHomeScore} - ${e.intAwayScore}</b> ` : ' × ';
  return `<div class="match"><b>${esc(tn(e.strHomeTeam))}</b>${sc}<b>${esc(tn(e.strAwayTeam))}</b><br><small>${esc(when)}${e.strVenue ? ' · ' + esc(e.strVenue) : ''}</small></div>`;
}
function renderFootball() {
  const show = !!FOOT;
  $('#footCard').classList.toggle('hidden', !show); $('#secFoot').classList.toggle('hidden', !show);
  if (!show) return;
  const F = FOOT;
  $('#fSeason').textContent = F.season ? 'موسم ' + F.season : '';
  $('#fToday').innerHTML = F.today.length ? F.today.map(e => evLine(e, true)).join('') : '<div class="match">لا مباريات اليوم</div>';
  $('#fNext').innerHTML = F.next.length ? F.next.map(e => evLine(e)).join('') : '<div class="match">—</div>';
  $('#fLast').innerHTML = F.past.length ? F.past.map(e => evLine(e, true)).join('') : '<div class="match">—</div>';
  $('#fTable').innerHTML = '<tr><th>#</th><th>الفريق</th><th>لعب</th><th>فارق</th><th>نقاط</th></tr>' + F.table.map(r => `<tr><td>${r.intRank}</td><td>${esc(tn(r.strTeam))}</td><td>${r.intPlayed}</td><td dir="ltr">${r.intGoalDifference}</td><td><b>${r.intPoints}</b></td></tr>`).join('');
  const upd = F.table[0]?.dateUpdated;
  $('#fNote').innerHTML = `المصدر: TheSportsDB.com (واجهة مجانية عامة)${upd ? ' · آخر تحديث للجدول ' + esc(upd) + ' UTC' : ''}.<br>الواجهة المجانية تعرض أعلى ${F.table.length} فرق فقط ومباراة قادمة واحدة؛ لا نعرض أي بيانات غير صادرة من المصدر.`;
  let pv = '';
  if (F.today.length) pv = '<div>اليوم:</div>' + F.today.slice(0, 2).map(e => `<div class="m"><b>${esc(tn(e.strHomeTeam))}</b> × <b>${esc(tn(e.strAwayTeam))}</b></div>`).join('');
  else if (F.next.length) { const e = F.next[0], t = evTime(e); pv = `<div>القادمة: <b>${esc(tn(e.strHomeTeam))}</b> × <b>${esc(tn(e.strAwayTeam))}</b></div><div><small>${esc(e.dateEvent)}${t ? ' · ' + fmtTime(t) : ''}</small></div>`; }
  if (F.table.length) pv += '<div style="margin-top:8px">' + F.table.slice(0, 3).map(r => `<div class="m"><span>${r.intRank}. ${esc(tn(r.strTeam))}</span><b>${r.intPoints}</b></div>`).join('') + '</div>';
  $('#footPreview').innerHTML = pv;
}

/* ── apps & smart suggestions ───────────────────────────── */
let APPS = [];
/* tvOS-style tile: 16:9 rounded art, full-bleed banner or icon on a Palette-derived gradient. */
const TINT = {};
function tintFallback(a) { // JS dominant colour when the bridge did not supply one (preview / old builds)
  return new Promise(res => { const im = new Image(); im.onload = () => { try {
    const cv = document.createElement('canvas'); cv.width = cv.height = 24; const g = cv.getContext('2d'); g.drawImage(im, 0, 0, 24, 24);
    const d = g.getImageData(0, 0, 24, 24).data, bins = {};
    for (let i = 0; i < d.length; i += 4) { if (d[i + 3] < 200) continue; const r = d[i], gg = d[i + 1], b = d[i + 2], mx = Math.max(r, gg, b), mn = Math.min(r, gg, b);
      const k = (r >> 5) + ',' + (gg >> 5) + ',' + (b >> 5); const w = 1 + (mx - mn) / 64; (bins[k] = bins[k] || [0, 0, 0, 0]); bins[k][0] += r * w; bins[k][1] += gg * w; bins[k][2] += b * w; bins[k][3] += w; }
    const top = Object.values(bins).sort((x, y) => y[3] - x[3])[0]; if (!top) return res(null);
    const c = top.slice(0, 3).map(v => Math.round(v / top[3])); res(c); } catch { res(null); } }; im.onerror = () => res(null); im.src = a.icon; });
}
const hex = c => '#' + c.map(v => Math.max(0, Math.min(255, v)).toString(16).padStart(2, '0')).join('');
const shade = (h, f) => { const n = parseInt(h.slice(1), 16); return hex([n >> 16, (n >> 8) & 255, n & 255].map(v => Math.round(v * f))); };
function artStyle(c1, c2) { c2 = c2 || shade(c1, .55); return `--c1:${c1};--c2:${c2}`; }
function tileHTML(a) {
  const k = esc(a.pkg);
  const full = a.banner || a.art; // app's own TV banner, else its adaptive icon composed full-bleed natively
  if (full) return `<div class="tv" data-f data-pkg="${k}" tabindex="-1"><div class="art banner"><img src="${full}"><i class="gloss"></i></div><span class="lbl">${esc(a.label)}</span></div>`;
  const c1 = a.c1 || TINT[a.pkg] && TINT[a.pkg][0], c2 = a.c2 || TINT[a.pkg] && TINT[a.pkg][1];
  return `<div class="tv" data-f data-pkg="${k}" tabindex="-1"><div class="art icon"${c1 ? ` style="${artStyle(c1, c2)}"` : ''}><img src="${a.icon}"><i class="gloss"></i></div><span class="lbl">${esc(a.label)}</span></div>`;
}
async function tintApps() {
  await Promise.all(APPS.filter(a => !a.banner && !a.art && !a.c1 && !TINT[a.pkg]).map(async a => { const c = await tintFallback(a); if (c) { const h = hex(c); TINT[a.pkg] = [h, shade(h, .5)]; } }));
  $$('.tv[data-pkg] .art.icon:not([style])').forEach(el => { const t = TINT[el.parentNode.dataset.pkg]; if (t) el.setAttribute('style', artStyle(t[0], t[1])); });
}
async function loadApps() {
  try { APPS = JSON.parse(await call('apps')); } catch { APPS = []; }
  APPS.sort((a, b) => (b.tv - a.tv) || a.label.localeCompare(b.label, 'ar'));
  // persisted user order; apps never seen before are appended at the end
  let ord = []; try { ord = JSON.parse(pref('apps.order', '[]')); } catch { }
  const known = ord.filter(p => APPS.some(a => a.pkg === p)); const fresh = APPS.filter(a => !known.includes(a.pkg)).map(a => a.pkg);
  const first = !ord.length; ord = known.concat(fresh);
  if (fresh.length || known.length !== JSON.parse(pref('apps.order', '[]') || '[]').length) setPref('apps.order', JSON.stringify(ord));
  APPS.sort((a, b) => ord.indexOf(a.pkg) - ord.indexOf(b.pkg));
  if (!first && fresh.length && loadApps.done) toast('أُضيف: ' + fresh.map(p => APPS.find(a => a.pkg === p).label).join('، '));
  loadApps.done = true;
  renderAppRow();
  renderSuggestions(); tintApps();
}
function renderAppRow() {
  const keep = cur && cur.dataset.pkg && cur.closest('#appRow') ? cur.dataset.pkg : null;
  $('#appRow').innerHTML = APPS.length ? APPS.map(tileHTML).join('') : '<div class="empty">لا توجد تطبيقات</div>';
  $('#favRow').innerHTML = APPS.slice(0, 5).map(tileHTML).join('');
  if (keep) { const el = $(`#appRow [data-pkg="${CSS.escape(keep)}"]`); cur = null; if (el) setFocus(el); else focusFirst(); }
  if (MOVING) { const el = $(`#appRow [data-pkg="${CSS.escape(MOVING)}"]`); el && el.classList.add('moving'); }
}
// live refresh when packages are added/removed/updated (called from MainActivity's receiver)
window.onAppsChanged = () => { clearTimeout(window._ac); window._ac = setTimeout(() => loadApps(), 600); };
/* move mode: long-press OK on an app tile, then ◀ ▶ to reorder, OK/Back to finish */
let MOVING = null;
function startMove(el) { MOVING = el.dataset.pkg; el.classList.add('moving'); toast('وضع الترتيب: استخدم ◀ ▶ لنقل التطبيق ثم اضغط موافق'); }
function stepMove(dir) {
  const i = APPS.findIndex(a => a.pkg === MOVING); const rtl = getComputedStyle($('#appRow')).direction === 'rtl';
  const j = i + ((dir === 'right') !== rtl ? 1 : -1); if (j < 0 || j >= APPS.length) return;
  [APPS[i], APPS[j]] = [APPS[j], APPS[i]]; renderAppRow();
}
function endMove() { setPref('apps.order', JSON.stringify(APPS.map(a => a.pkg))); $$('.moving').forEach(e => e.classList.remove('moving')); MOVING = null; toast('حُفظ ترتيب التطبيقات'); }
function slot() { return Math.floor(uaeParts().h / 3); }
function localSuggest() {
  let U = {}; try { U = JSON.parse(A.usage()); } catch { }
  const s = slot(), now = Date.now();
  const score = a => { const u = U[a.pkg] || {}; const rec = u.last ? Math.max(0, 1 - (now - u.last) / (7 * 864e5)) : 0; return (u[s] || 0) * 3 + ((u[(s + 7) % 8] || 0) + (u[(s + 1) % 8] || 0)) + (u.total || 0) * 0.4 + rec * 2 + (a.tv ? 0.1 : 0); };
  return APPS.map(a => [a, score(a)]).sort((x, y) => y[1] - x[1]).slice(0, 7).map(x => x[0]);
}
const SLOT_AR = ['بعد منتصف الليل', 'الفجر', 'الصباح', 'الضحى', 'الظهيرة', 'العصر', 'المساء', 'الليل'];
function renderSuggestions(list, why) {
  list = list || localSuggest();
  $('#sugRow').innerHTML = list.length ? list.map(tileHTML).join('') : '<div class="empty">ستظهر هنا اقتراحات حسب استخدامك ووقت اليوم</div>';
  tintApps(); $('#sugWhy').textContent = why || `حسب استخدامك في وقت ${SLOT_AR[slot()]}`;
}
async function aiSuggest() {
  if (!APPS.length || !aiReady()) return;
  const key = 'sug.' + uaeParts().d + '.' + slot();
  try { const c = JSON.parse(localStorage[key] || 'null'); if (c) { const l = c.p.map(p => APPS.find(a => a.pkg === p)).filter(Boolean); if (l.length) return renderSuggestions(l, c.why); } } catch { }
  let U = {}; try { U = JSON.parse(A.usage()); } catch { }
  const np = nextPrayer();
  const ctx = { time: fmtTime(Date.now()), weekday: DAYS[uaeParts().wd], slot: SLOT_AR[slot()], nextPrayer: PNAMES[np.i] + ' ' + fmtTime(np.t), football_today: FOOT?.today?.length || 0,
    apps: APPS.slice(0, 60).map(a => ({ pkg: a.pkg, name: a.label, uses_this_slot: U[a.pkg]?.[slot()] || 0, uses_total: U[a.pkg]?.total || 0 })) };
  try {
    const r = await ai('أنت محرك اقتراحات لتلفزيون منزلي. أعد JSON فقط بالشكل {"p":["package",...],"why":"سبب قصير بالعربية لا يتجاوز 8 كلمات"} واختر 6 تطبيقات من القائمة فقط بناءً على الوقت والاستخدام.', [{ role: 'user', text: JSON.stringify(ctx) }]);
    const j = JSON.parse(r.replace(/^```(json)?|```$/gm, '').trim());
    const l = (j.p || []).map(p => APPS.find(a => a.pkg === p)).filter(Boolean);
    if (l.length) { localStorage[key] = JSON.stringify({ p: l.map(a => a.pkg), why: '✦ ' + (j.why || '') }); renderSuggestions(l, '✦ ' + (j.why || '')); }
  } catch (e) { /* keep local suggestions */ }
}

/* ── AI ─────────────────────────────────────────────────── */
let AIINFO = {};
const aiReady = () => !!AIINFO.hasKey;
function ai(system, messages) { return call('ai', system, JSON.stringify(messages)); }
function contextBlock() {
  const now = Date.now(), dl = dateLines(now), np = nextPrayer();
  const pt = np.today.map((t, i) => PNAMES[i] + ' ' + fmtTime(t)).join('، ');
  const wx = WX ? `${Math.round(WX.current.temperature_2m)}° ${wmo(WX.current.weather_code)[0]}، العظمى ${Math.round(WX.daily.temperature_2m_max[0])}° والصغرى ${Math.round(WX.daily.temperature_2m_min[0])}° (المصدر Open-Meteo)` : 'غير متاح';
  let ft = 'لا توجد بيانات كرة قدم متاحة';
  if (FOOT) ft = `مباريات اليوم: ${FOOT.today.length ? FOOT.today.map(e => tn(e.strHomeTeam) + ' × ' + tn(e.strAwayTeam) + (evTime(e) ? ' ' + fmtTime(evTime(e)) : '')).join('، ') : 'لا يوجد'}؛ القادمة: ${FOOT.next.map(e => tn(e.strHomeTeam) + ' × ' + tn(e.strAwayTeam) + ' ' + e.dateEvent).join('، ') || '—'}؛ المتصدرون: ${FOOT.table.slice(0, 5).map(r => r.intRank + '. ' + tn(r.strTeam) + ' (' + r.intPoints + ' نقطة)').join('، ')} (المصدر TheSportsDB)`;
  return `الوقت الآن ${fmtTime(now)}، ${dl.greg} الموافق ${dl.hijri}. المدينة: ${city().ar}.\nمواقيت الصلاة اليوم: ${pt}. الصلاة القادمة: ${PNAMES[np.i]} ${fmtTime(np.t)}.\nالطقس: ${wx}.\nكرة القدم (دوري المحترفين الإماراتي): ${ft}.`;
}

/* assistant */
const chatLog = [];
function addMsg(cls, text) { const d = document.createElement('div'); d.className = 'msg ' + cls; d.textContent = text; $('#chat').appendChild(d); $('#chat').scrollTop = 1e9; return d; }
async function ask(q) {
  q = (q || '').trim(); if (!q) return;
  addMsg('u', q); chatLog.push({ role: 'user', text: q });
  const wait = addMsg('a', '…');
  const appsList = APPS.slice(0, 50).map(a => `${a.label} = ${a.pkg}`).join('\n');
  const sys = `أنت «الكود»، مساعد صوتي عربي على تلفزيون منزلي في الإمارات. أجب بالعربية الفصحى المبسطة وبإيجاز (3-5 جمل) لأن الإجابة تُعرض على شاشة التلفزيون.
لا تخترع معلومات: إذا لم تكن المعلومة في السياق أو في معرفتك المؤكدة فقل ذلك بوضوح. لا تختلق نتائج مباريات أو مواقيت.
إذا طلب المستخدم فتح تطبيق من القائمة، أضف في آخر ردك السطر [[open:اسم_الحزمة]] بالضبط.
السياق الحالي:
${contextBlock()}
التطبيقات المثبتة:
${appsList}`;
  try {
    let r = await ai(sys, chatLog.slice(-10));
    const m = r.match(/\[\[open:([\w.]+)\]\]/);
    r = r.replace(/\[\[open:[\w.]+\]\]/g, '').trim();
    wait.textContent = r; chatLog.push({ role: 'assistant', text: r });
    if (m && APPS.some(a => a.pkg === m[1])) setTimeout(() => A.launch(m[1]), 900);
  } catch (e) { wait.className = 'msg e'; wait.textContent = e.message; chatLog.pop(); }
}
window.onVoice = t => { if (t) { $('#askIn').value = ''; ask(t); } else toast('لم يتم التعرف على الصوت'); };
window.openAssistant = voice => { open('assistant'); if (voice && A.hasVoice()) A.listen(); };

/* briefing */
async function briefing() {
  $('#briefBody').textContent = aiReady() ? 'جارٍ إعداد الموجز…' : '';
  $('#briefSrc').innerHTML = 'المصادر: الطقس من Open-Meteo.com · المواقيت محسوبة على الجهاز (طريقة الإمارات) · كرة القدم من TheSportsDB.com · الصياغة بالذكاء الاصطناعي (' + esc(AIINFO.provider || '') + ')';
  if (!aiReady()) { $('#briefBody').textContent = contextBlock(); return; }
  try {
    const r = await ai('أنت مذيع موجز صباحي/مسائي لأسرة إماراتية. اكتب موجزاً دافئاً بالعربية في 5 إلى 7 أسطر قصيرة: تحية تناسب الوقت، التاريخان، الطقس، الصلاة القادمة ومواقيت اليوم، ثم مباريات الدوري إن وجدت. استخدم فقط البيانات المعطاة ولا تضف أرقاماً من عندك. لا تستخدم تنسيق ماركداون.', [{ role: 'user', text: contextBlock() }]);
    $('#briefBody').textContent = r; $('#briefPreview').textContent = r.split('\n').filter(Boolean).slice(0, 3).join('\n');
    localStorage.brief = JSON.stringify({ at: Date.now(), r });
  } catch (e) { $('#briefBody').textContent = e.message + '\n\n' + contextBlock(); }
}

/* ── Quran (Tanzil text) & verse explanation ────────────── */
let SURAS = [], curSura = null, curVerses = [];
function loadSuras() { SURAS = window.SURAS_DATA || []; }
function renderSuraGrid() {
  $('#suraGrid').innerHTML = SURAS.map(s => `<div class="sura" data-f data-sura="${s.n}" tabindex="-1">${s.n}. ${esc(s.name)}<small>${s.type} · ${s.ayas} آية</small></div>`).join('');
}
const arNum = n => String(n).replace(/\d/g, d => '٠١٢٣٤٥٦٧٨٩'[d]);
async function openSura(n) {
  curSura = SURAS[n - 1]; $('#rTitle').textContent = 'سورة ' + curSura.name;
  $('#verses').innerHTML = '…'; open('reader');
  try {
    curVerses = JSON.parse(await call('sura', n));
    $('#verses').innerHTML = curVerses.map((v, i) => `<span class="v" data-f data-aya="${i + 1}" tabindex="-1">${esc(v)}<i>﴿${arNum(i + 1)}﴾</i></span> `).join('');
    focusFirst();
  } catch (e) { $('#verses').textContent = e.message; }
}
async function explain(aya) {
  const s = curSura, text = curVerses[aya - 1];
  $('#exVerse').textContent = `${text} ﴿${s.name}: ${arNum(aya)}﴾`;
  $('#exBody').textContent = 'جارٍ جلب التفسير…'; $('#exSrc').innerHTML = ''; open('explain');
  const ref = `${s.n}:${aya}`, srcs = [];
  for (const [ed, name] of [['ar.muyassar', 'التفسير الميسر — مجمع الملك فهد لطباعة المصحف الشريف'], ['ar.jalalayn', 'تفسير الجلالين']]) {
    try { const j = await getJSON(`https://api.alquran.cloud/v1/ayah/${ref}/${ed}`); if (j?.data?.text) srcs.push({ name, text: j.data.text, url: `https://api.alquran.cloud/v1/ayah/${ref}/${ed}` }); } catch { }
  }
  if (!srcs.length) { $('#exBody').textContent = 'تعذّر جلب نص التفسير (تحقق من الاتصال). لن نعرض شرحاً بلا مصدر.'; return; }
  const srcHTML = '<b>المصادر:</b><br>' + srcs.map((x, i) => `[${i + 1}] ${esc(x.name)} — عبر alquran.cloud (${esc(ref)})`).join('<br>') + '<br>نص الآية: مشروع تنزيل tanzil.net';
  $('#exSrc').innerHTML = srcHTML;
  if (!aiReady()) { $('#exBody').textContent = srcs.map((x, i) => `[${i + 1}] ${x.text}`).join('\n\n'); return; }
  $('#exBody').textContent = 'جارٍ إعداد الشرح…';
  try {
    const r = await ai('أنت مساعد يشرح معاني آيات القرآن الكريم لعامة الناس بالعربية المبسطة. اعتمد فقط على نصوص التفاسير المرفقة ولا تضف أحكاماً أو أقوالاً من خارجها. اكتب شرحاً من 3 إلى 6 جمل، وضع بعد كل معلومة رقم مصدرها بين معقوفين مثل [1] أو [2]. لا تستخدم ماركداون. إذا لم تكفِ النصوص فقل ذلك.',
      [{ role: 'user', text: `الآية (${s.name} ${aya}): ${text}\n\n` + srcs.map((x, i) => `[${i + 1}] ${x.name}:\n${x.text}`).join('\n\n') }]);
    $('#exBody').textContent = r + '\n\n— نص التفسير الميسر [1]:\n' + srcs[0].text;
  } catch (e) { $('#exBody').textContent = e.message + '\n\n' + srcs.map((x, i) => `[${i + 1}] ${x.text}`).join('\n\n'); }
}

/* ── settings ───────────────────────────────────────────── */
function renderSettings() {
  $('#cityOpts').innerHTML = CITIES.map(c => `<button data-f data-pref="city" data-val="${c.id}">${c.ar}</button>`).join('');
  $('#bgOpts').innerHTML = `<button data-f data-pref="bg" data-val="auto">تلقائي (كل الصور)</button>` + PHOTOS.map(p => `<button data-f data-pref="bg" data-val="${p.file}">${esc(p.ar.split('—')[0].trim())}</button>`).join('');
  $$('[data-pref]').forEach(b => b.classList.toggle('sel', String(pref(b.dataset.pref, { city: 'abudhabi', adhan: 'on', bg: 'auto', clock: '12', idle: '15' }[b.dataset.pref])) === b.dataset.val));
  AIINFO = JSON.parse(A.aiInfo());
  $('#aiInfo').textContent = AIINFO.hasKey ? `المزوّد: ${AIINFO.provider} · النموذج: ${AIINFO.model}${AIINFO.override ? ' · مفتاح بديل' : ' · المفتاح المدمج'}` : 'لا يوجد مفتاح';
  $('#modelIn').value = pref('ai.model', '');
  $('#verInfo').textContent = 'الإصدار ' + A.version();
}
function renderCredits() {
  $('#credBody').innerHTML = `<img src="brand.png" style="height:60px;vertical-align:middle;margin-left:14px"><b>الكود TV</b> (Alcode TV) من مؤسسة الكود — واجهة رئيسية عربية إماراتية لأجهزة Android TV. الكود مفتوح المصدر (MIT).<br><br>
<b>الصور (Wikimedia Commons):</b><br>${PHOTOS.map(p => `• ${esc(p.ar)} — ${esc(p.artist)} — ${esc(p.license)}`).join('<br>')}<br>(تم قصّها وتصغيرها إلى 1920×1080؛ الروابط الكاملة في CREDITS.md)<br><br>
<b>نص القرآن الكريم:</b> مشروع تنزيل Tanzil Quran Text (Uthmani) — tanzil.net — يُستخدم دون تعديل وفق شروطه.<br>
<b>التفسير:</b> التفسير الميسر وتفسير الجلالين عبر واجهة alquran.cloud.<br>
<b>الخطوط:</b> Noto Kufi Arabic وAmiri Quran — رخصة SIL Open Font License 1.1.<br>
<b>الطقس:</b> Open-Meteo.com (CC BY 4.0). <b>كرة القدم:</b> TheSportsDB.com (الواجهة المجانية).<br>
<b>الذكاء الاصطناعي:</b> ${esc(AIINFO.provider || '—')}.`;
}
async function checkUpdate(manual) {
  if (manual) $('#updMsg').textContent = 'جارٍ البحث…';
  try {
    const u = JSON.parse(await call('checkUpdate'));
    if (u.newer) {
      $('#updMsg').innerHTML = `يتوفر الإصدار ${esc(u.latest)} `;
      const b = document.createElement('button'); b.setAttribute('data-f', ''); b.textContent = 'تثبيت التحديث';
      b.onclick = async () => { $('#updMsg').firstChild.textContent = 'جارٍ التنزيل… '; try { const r = await call('installUpdate', u.apk); if (r === 'needs-permission') toast('اسمح بتثبيت التطبيقات من «مجلس» ثم أعد المحاولة'); } catch (e) { toast(e.message); } };
      $('#updMsg').appendChild(b);
      if (!manual) toast(`يتوفر تحديث لالكود TV (${u.latest}) — من الإعدادات`);
    } else if (manual) $('#updMsg').textContent = `أنت على آخر إصدار (${u.current})`;
  } catch (e) { if (manual) $('#updMsg').textContent = 'تعذّر التحقق: ' + e.message; }
}

/* ── navigation (D-pad spatial focus) ───────────────────── */
let stack = ['home'], cur = null;
const layerEl = () => document.getElementById(stack[stack.length - 1]);
function focusables() { return [...layerEl().querySelectorAll('[data-f]')].filter(e => e.offsetParent !== null && !e.closest('.hidden')); }
function setFocus(el) {
  if (!el) return;
  if (cur) { cur.classList.remove('focus'); cur.style.removeProperty('--nx'); }
  if (cur && el.classList.contains('tv')) { const a = cur.getBoundingClientRect(), b = el.getBoundingClientRect(); el.style.setProperty('--nx', Math.sign(b.left - a.left) || 0); el.style.setProperty('--ny', Math.sign(b.top - a.top) || 0); }
  cur = el; el.classList.add('focus');
  if (el.classList.contains('tv')) { el.classList.remove('nudge'); void el.offsetWidth; el.classList.add('nudge'); }
  if (el.tagName === 'INPUT') el.focus(); else if (document.activeElement && document.activeElement.tagName === 'INPUT') document.activeElement.blur();
  el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
  if (el.closest('.grid')) { el.scrollIntoView({ block: 'center', behavior: 'smooth' }); return; }
  if (stack.length === 1) { const row = el.closest('.row, #hero, #top'); if (row) row.scrollIntoView({ block: row.id === 'top' || row.id === 'hero' ? 'start' : 'center', behavior: 'smooth' }); }
}
function focusFirst() { const f = focusables(); setFocus(f[0]); }
function move(dir) {
  const list = focusables(); if (!cur || !list.includes(cur)) return setFocus(list[0]);
  const a = cur.getBoundingClientRect(), ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  let best = null, bs = Infinity;
  for (const e of list) {
    if (e === cur) continue;
    const b = e.getBoundingClientRect(), bx = b.left + b.width / 2, by = b.top + b.height / 2;
    const dx = bx - ax, dy = by - ay;
    let main, cross;
    if (dir === 'left') { if (b.right > a.left + 1 && dx >= -4) continue; main = -dx; cross = Math.abs(dy); }
    if (dir === 'right') { if (b.left < a.right - 1 && dx <= 4) continue; main = dx; cross = Math.abs(dy); }
    if (dir === 'up') { if (b.bottom > a.top + 1 && dy >= -4) continue; main = -dy; cross = Math.abs(dx); }
    if (dir === 'down') { if (b.top < a.bottom - 1 && dy <= 4) continue; main = dy; cross = Math.abs(dx); }
    if (main <= 0) continue;
    const s = main + cross * ((dir === 'up' || dir === 'down') ? 0.6 : 2.5);
    if (s < bs) { bs = s; best = e; }
  }
  if (best) setFocus(best);
}
function open(id) {
  if (stack[stack.length - 1] !== id) stack.push(id);
  $$('.layer').forEach(l => l.classList.toggle('active', l.id === id || (id !== 'home' && l.id === 'home' && false)));
  document.body.classList.toggle('amb', id === 'ambient');
  if (id === 'settings') renderSettings();
  if (id === 'credits') renderCredits();
  if (id === 'quran' && !$('#suraGrid').children.length) renderSuraGrid();
  if (id === 'assistant') $('#aiProv').textContent = aiReady() ? `(${AIINFO.provider})` : '(أضف مفتاحاً من الإعدادات)';
  if (id === 'prayers') renderPrayerTable();
  cur = null; setTimeout(focusFirst, 30);
}
function back() {
  if (stack.length > 1) { const prev = stack.pop(); const id = stack[stack.length - 1]; $$('.layer').forEach(l => l.classList.toggle('active', l.id === id)); document.body.classList.remove('amb'); cur = null; setTimeout(focusFirst, 30); if (A && A.stopSpeak) A.stopSpeak(); }
}
window.onBack = back;
window.onHome = () => { while (stack.length > 1) back(); };
function renderPrayerTable() {
  const np = nextPrayer(); $('#ptCity').textContent = city().ar;
  $('#ptTable').innerHTML = np.today.map((t, i) => `<div class="${t === np.t ? 'next' : ''}">${PNAMES[i]}<b>${fmtTime(t, false)}</b></div>`).join('');
}

function activate(el) {
  if (!el) return;
  if (el.dataset.pkg) return A.launch(el.dataset.pkg);
  if (el.dataset.sura) return openSura(+el.dataset.sura);
  if (el.dataset.aya) return explain(+el.dataset.aya);
  if (el.dataset.pref) { setPref(el.dataset.pref, el.dataset.val); renderSettings(); setFocus($(`[data-pref="${el.dataset.pref}"][data-val="${CSS.escape(el.dataset.val)}"]`));
    if (el.dataset.pref === 'city') { loadWeather(); tick(); } if (el.dataset.pref === 'bg') showBg(0); resetIdle(); return; }
  const act = el.dataset.act;
  if (act === 'briefing') { open('briefing'); briefing(); return; }
  if (act === 'ambient') { open('ambient'); return; }
  if (act) return open(act);
  if (el.tagName === 'INPUT') { el.focus(); return; }
  el.click();
}
document.addEventListener('keydown', e => {
  resetIdle();
  if (stack[stack.length - 1] === 'ambient' && !IS_DREAM) { back(); e.preventDefault(); return; }
  const k = e.key;
  if (MOVING) { if (k === 'ArrowLeft' || k === 'ArrowRight') stepMove(k === 'ArrowLeft' ? 'left' : 'right'); else if (k === 'Enter' || k === 'Escape' || k === 'Backspace' || k === 'GoBack') { if (!e.repeat) endMove(); } e.preventDefault(); return; }
  if ((k === 'Enter' || k === 'ContextMenu') && cur && cur.dataset.pkg && cur.closest('#appRow')) {
    e.preventDefault(); if (k === 'ContextMenu') return startMove(cur);
    if (!e.repeat && !LP) { const el = cur; LP = setTimeout(() => { LP = 'fired'; startMove(el); }, 650); } return; }
  const inInput = document.activeElement && document.activeElement.tagName === 'INPUT';
  if (k === 'ArrowLeft' || k === 'ArrowRight') { if (inInput && document.activeElement.value) return; move(k === 'ArrowLeft' ? 'left' : 'right'); e.preventDefault(); }
  else if (k === 'ArrowUp' || k === 'ArrowDown') { if (inInput) document.activeElement.blur(); move(k === 'ArrowUp' ? 'up' : 'down'); e.preventDefault(); }
  else if (k === 'Enter' || k === ' ' && !inInput) { if (inInput) { if (cur && cur.id === 'askIn') { ask(cur.value); cur.value = ''; } else document.activeElement.blur(); } else activate(cur); e.preventDefault(); }
  else if (k === 'Escape' || k === 'Backspace' && !inInput || k === 'GoBack' || k === 'BrowserBack') { back(); e.preventDefault(); }
});
let LP = null;
document.addEventListener('keyup', e => { if (e.key === 'Enter' && LP) { if (LP !== 'fired') { clearTimeout(LP); activate(cur); } LP = null; } });
document.addEventListener('click', e => { const t = e.target.closest('[data-f]'); if (t && !t.matches('button:not([data-pref])') ) { setFocus(t); activate(t); } else if (t) setFocus(t); });

/* idle → ambient */
let idleT = null;
function resetIdle() { clearTimeout(idleT); const m = +pref('idle', '15'); if (m > 0 && !IS_DREAM) idleT = setTimeout(() => { if (stack[stack.length - 1] !== 'ambient') open('ambient'); }, m * 60000); }

let toastT; function toast(t) { $('#toast').textContent = t; $('#toast').classList.add('on'); clearTimeout(toastT); toastT = setTimeout(() => $('#toast').classList.remove('on'), 4200); }
window.onAdhan = i => { toast(`حان الآن موعد أذان ${PNAMES[i]} — حسب توقيت ${city().ar}`); tick(); };
window.onResumeApp = () => { tick(); loadApps(); };

/* buttons */
function wire() {
  $('#askBtn').onclick = () => { ask($('#askIn').value); $('#askIn').value = ''; };
  $('#micBtn').onclick = () => { if (A.hasVoice()) A.listen(); else toast('لا يدعم هذا الجهاز الإدخال الصوتي — اكتب سؤالك'); };
  $('#briefSpeak').onclick = () => A.speak($('#briefBody').textContent);
  $('#briefAgain').onclick = briefing;
  $('#exBack').onclick = $('#fBack').onclick = $('#wBack').onclick = $('#ptBack').onclick = back;
  $('#testAdhan').onclick = () => { A.testAdhan(); toast('أُرسل تنبيه تجريبي'); };
  $('#dreamSet').onclick = () => A.openDreamSettings();
  $('#sysSet').onclick = () => A.openSystemSettings();
  $('#credBtn').onclick = () => open('credits');
  $('#keySave').onclick = () => { const v = $('#keyIn').value.trim(); if (!v) return toast('أدخل المفتاح أولاً'); A.setAiKey(v); $('#keyIn').value = ''; renderSettings(); toast('حُفظ المفتاح البديل'); };
  $('#keyClear').onclick = () => { A.setAiKey(''); renderSettings(); toast('سيُستخدم المفتاح المدمج'); };
  $('#modelSave').onclick = () => { setPref('ai.model', $('#modelIn').value.trim()); renderSettings(); toast('حُفظ النموذج'); };
  $('#updBtn').onclick = () => checkUpdate(true);
  const chips = ['وش الطقس اليوم؟', 'متى صلاة المغرب؟', 'من متصدر الدوري؟', 'افتح يوتيوب', 'اقترح لي شيئاً أشاهده الليلة'];
  $('#chips').innerHTML = chips.map(c => `<button data-f>${c}</button>`).join('');
  $$('#chips button').forEach(b => b.onclick = () => ask(b.textContent));
}

/* ── boot ───────────────────────────────────────────────── */
function boot() {
  if (!A) { document.body.innerHTML = '<p style="padding:80px;font-size:40px">يعمل داخل تطبيق الكود TV فقط</p>'; return; }
  loadPrefs();
  try { CITIES = JSON.parse(A.cities()); } catch { CITIES = [{ id: 'abudhabi', ar: 'أبوظبي', lat: 24.4539, lng: 54.3773 }]; }
  startBg(); tick(); setInterval(tick, 15000);
  if (IS_DREAM) { stack = ['ambient']; $$('.layer').forEach(l => l.classList.toggle('active', l.id === 'ambient')); document.body.classList.add('amb'); return; }
  try { AIINFO = JSON.parse(A.aiInfo()); } catch { }
  try { const b = JSON.parse(localStorage.brief || 'null'); if (b && Date.now() - b.at < 6 * 3600e3) $('#briefPreview').textContent = b.r.split('\n').filter(Boolean).slice(0, 3).join('\n'); } catch { }
  wire(); loadSuras();
  loadWeather(); loadFootball().then(() => aiSuggest());
  loadApps().then(() => { if (!cur) focusFirst(); });
  setInterval(loadWeather, 30 * 60000); setInterval(loadFootball, 15 * 60000);
  setTimeout(() => checkUpdate(false), 20000);
  focusFirst(); resetIdle();
}
boot();
