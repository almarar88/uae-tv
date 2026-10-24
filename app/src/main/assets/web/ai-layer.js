/* Majlis 1.1 — AI layer, news, Liwaiptv hub, Ramadan, weather alerts, kids mode.
   Rule: AI never invents facts. It only (a) chooses among real items/tools, (b) rewrites given text.
   Every AI output is tagged ✦ and every fact carries its source. Everything degrades to local logic offline. */
'use strict';
const AITAG = '<span class="aitag">✦ ذكاء اصطناعي</span>';
const raw = url => call('get', url);
const ago = t => { const m = Math.round((Date.now() - t) / 60000); return m < 60 ? `قبل ${Math.max(1, m)} دقيقة` : m < 1440 ? `قبل ${Math.round(m / 60)} ساعة` : fmtTime(t, false); };
const cache = (k, v) => v === undefined ? (() => { try { return JSON.parse(localStorage[k] || 'null'); } catch { return null; } })() : (localStorage[k] = JSON.stringify(v));
const aiJSON = async (sys, user) => { const r = await ai(sys + '\nأعد JSON صالحاً فقط دون أي نص آخر.', [{ role: 'user', text: typeof user === 'string' ? user : JSON.stringify(user) }]); return JSON.parse(r.replace(/^```(json)?|```$/gm, '').trim()); };
const online = () => navigator.onLine !== false;

/* ════════ NEWS (verified feeds, 24 Oct 2026) ════════ */
const FEEDS = [
  { src: 'الإمارات اليوم', url: 'https://www.emaratalyoum.com/1.533091?ot=ot.AjaxPageLayout', c: '#9b1c24', cat: 'محلي' },
  { src: 'سكاي نيوز عربية (أبوظبي)', url: 'https://www.skynewsarabia.com/rss.xml', c: '#0b4a8b' },
  { src: 'The National', url: 'https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml', c: '#1d1d1d', en: true },
];
const NCATS = ['محلي', 'اقتصاد', 'رياضة', 'طقس', 'حكومة', 'عالمي'];
let NEWS = [], NCAT = 'الكل';
function guessCat(t) {
  if (/مباراة|دوري|كأس|منتخب|لاعب|نادي|football|league|match/i.test(t)) return 'رياضة';
  if (/طقس|أمطار|حرارة|غبار|ضباب|المركز الوطني للأرصاد|weather|rain|fog/i.test(t)) return 'طقس';
  if (/اقتصاد|أسهم|نفط|استثمار|درهم|سوق|بنك|economy|market|oil|stocks/i.test(t)) return 'اقتصاد';
  if (/رئيس الدولة|ولي عهد|مجلس الوزراء|وزارة|حكومة|مرسوم|government|ministry|sheikh/i.test(t)) return 'حكومة';
  if (/الإمارات|أبوظبي|دبي|الشارقة|عجمان|الفجيرة|رأس الخيمة|العين|UAE|Dubai|Abu Dhabi/i.test(t)) return 'محلي';
  return 'عالمي';
}
async function loadNews() {
  const all = [];
  await Promise.all(FEEDS.map(async f => {
    try {
      const x = new DOMParser().parseFromString(await raw(f.url), 'text/xml');
      [...x.querySelectorAll('item')].slice(0, 25).forEach(it => {
        const g = s => it.querySelector(s)?.textContent?.trim() || '';
        const title = g('title'); if (!title) return;
        const media = it.querySelector('enclosure[url], content[url], thumbnail[url]');
        const desc = g('description').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').slice(0, 400);
        all.push({ title, link: g('link'), t: Date.parse(g('pubDate')) || Date.now(), src: f.src, c: f.c, en: !!f.en, desc, img: media ? media.getAttribute('url') : '', cat: f.cat || guessCat(title + ' ' + desc) });
      });
    } catch (e) { /* feed down → silently skipped, others still shown */ }
  }));
  if (all.length) { NEWS = all.filter((n, i) => all.findIndex(m => m.title === n.title) === i).sort((a, b) => b.t - a.t).slice(0, 60); cache('news', { at: Date.now(), NEWS }); }
  else { const c = cache('news'); if (c) NEWS = c.NEWS; }
  applyNewsAI(); renderNews();
  if (aiReady() && online()) aiNews().catch(() => { });
}
function applyNewsAI() { const m = cache('news.ai') || {}; NEWS.forEach(n => { const a = m[n.link || n.title]; if (a) { n.cat = a.cat; n.sum = a.sum; } }); }
async function aiNews(pass = 0) {
  const m = cache('news.ai') || {}; const todo = NEWS.filter(n => !m[n.link || n.title]).slice(0, 8); if (!todo.length) return;
  const j = await aiJSON(`أنت محرر أخبار. لكل خبر: صنّفه في واحدة فقط من [${NCATS.join('، ')}] واكتب ملخصاً عربياً من جملة واحدة (حتى 22 كلمة) يعتمد حصراً على العنوان والوصف المعطيين دون إضافة أي معلومة أو رقم أو اسم غير موجود. إن كان الخبر بالإنجليزية فترجم الملخص للعربية. الشكل: [{"i":0,"cat":"…","sum":"…"}]`,
    todo.map((n, i) => ({ i, title: n.title, desc: n.desc.slice(0, 300) })));
  (Array.isArray(j) ? j : []).forEach(r => { const n = todo[r.i]; if (n && NCATS.includes(r.cat)) m[n.link || n.title] = { cat: r.cat, sum: String(r.sum || '').slice(0, 220) }; });
  const keys = Object.keys(m); if (keys.length > 400) keys.slice(0, keys.length - 400).forEach(k => delete m[k]);
  cache('news.ai', m); applyNewsAI(); renderNews();
  if (pass < 3) return aiNews(pass + 1);
}
function newsCard(n, i) {
  return `<div class="tv nws" data-f data-news="${i}" tabindex="-1"><div class="art" style="--c1:${n.c};--c2:#0d0a06">${n.img ? `<img class="cov" src="${esc(n.img)}" onerror="this.remove()">` : ''}<div class="ntx"><em>${esc(n.cat)}</em><b>${esc(n.title)}</b></div><i class="gloss"></i></div><span class="lbl">${esc(n.src)} · ${ago(n.t)}</span></div>`;
}
function renderNews() {
  if (!NEWS.length) { $('#newsRow').innerHTML = '<div class="empty">تعذّر جلب الأخبار حالياً — تحقق من الاتصال</div>'; return; }
  const pick = KIDS() ? [] : NEWS.slice(0, 12);
  $('#newsRow').innerHTML = pick.map(n => newsCard(n, NEWS.indexOf(n))).join('');
  const br = NEWS.filter(n => Date.now() - n.t < 6 * 3600e3).slice(0, 8);
  $('#ticker').classList.toggle('hidden', !br.length || KIDS());
  $('#tkTx').innerHTML = br.map(n => `<i>${esc(n.src)}</i> ${esc(n.title)} <s>${fmtTime(n.t, false)}</s>`).join('<u>◆</u>');
  $('#tkTx').style.animationDuration = Math.max(40, br.length * 14) + 's';
  if (stack[stack.length - 1] === 'news') renderNewsPanel();
}
function renderNewsPanel() {
  $('#newsMeta').innerHTML = `${NEWS.length} خبراً · يتحدّث كل 15 دقيقة · التصنيف والملخصات ${AITAG}`;
  $('#newsCats').innerHTML = ['الكل', ...NCATS].map(c => `<button data-f data-ncat="${c}" class="${c === NCAT ? 'sel' : ''}">${c}</button>`).join('');
  const list = NEWS.filter(n => NCAT === 'الكل' || n.cat === NCAT);
  $('#newsList').innerHTML = list.length ? list.map(n => `<div class="nitem" data-f data-nitem="${NEWS.indexOf(n)}" tabindex="-1"><div class="nh"><em>${esc(n.cat)}</em><b>${esc(n.title)}</b></div>
    ${n.sum ? `<p>${AITAG} ${esc(n.sum)}</p>` : n.desc ? `<p>${esc(n.desc.slice(0, 200))}</p>` : ''}
    <small>المصدر: ${esc(n.src)} · ${fmtTime(n.t, false)} ${dateLines(n.t).greg} · <span class="lnk">${esc((n.link || '').replace(/^https?:\/\/(www\.)?/, '').slice(0, 70))}</span></small></div>`).join('') : '<div class="empty">لا أخبار في هذا التصنيف الآن</div>';
}

/* ════════ LIWAIPTV (Xtream) ════════ */
let IP = { live: [], vod: [], series: [] };
const hasIP = () => { try { return A.iptvHas(); } catch { return false; } };
const ipApi = (a, e = '') => call('iptvApi', a, e).then(JSON.parse);
const recent = () => cache('ip.recent') || [];
async function loadIptv() {
  const on = hasIP() && !KIDS();
  ['#rowLive', '#rowMovies', '#rowSeries'].forEach(r => $(r).classList.toggle('hidden', !on));
  $('#rowRecent').classList.toggle('hidden', !on || !recent().length);
  if (!on) return;
  try {
    const [live, vod, series] = await Promise.all([ipApi('get_live_streams'), ipApi('get_vod_streams'), ipApi('get_series')]);
    IP = { live: live || [], vod: (vod || []).sort((a, b) => b.added - a.added), series: (series || []).sort((a, b) => b.last_modified - a.last_modified) };
    cache('ip.lastok', Date.now());
  } catch (e) { toast('تعذّر الاتصال باشتراك Liwaiptv: ' + e.message); }
  renderIptvRows();
}
function ipItem(kind, x) {
  if (kind === 'live') return { kind, id: String(x.stream_id), name: x.name, img: x.stream_icon, ext: 'ts' };
  if (kind === 'movie') return { kind, id: String(x.stream_id), name: x.name, img: x.stream_icon, ext: x.container_extension || 'mp4', rating: x.rating };
  return { kind, id: String(x.series_id), name: x.name, img: x.cover, back: (x.backdrop_path || [])[0], rating: x.rating, year: (x.releaseDate || '').slice(0, 4), plot: x.plot, genre: x.genre };
}
const hue = s => { let h = 0; for (const c of String(s)) h = (h * 31 + c.codePointAt(0)) >>> 0; return h % 360; };
const initials = n => String(n).replace(/\b(HD|FHD|SD|4K|UHD)\b/gi, '').trim().split(/\s+/).slice(0, 2).map(w => [...w][0]).join('');
function ipCard(it, poster) {
  const d = esc(JSON.stringify(it));
  if (it.kind === 'live') return `<div class="tv ip ch" data-f data-ip="${d}" tabindex="-1"><div class="art" style="--c1:hsl(${hue(it.name)} 45% 34%);--c2:hsl(${hue(it.name)} 50% 12%)">${it.img ? `<img class="logo" src="${esc(it.img)}" onerror="this.nextElementSibling.hidden=false;this.remove()"><b class="ipn" hidden>${esc(initials(it.name))}</b>` : `<b class="ipn">${esc(initials(it.name))}</b>`}<i class="gloss"></i></div><span class="lbl">${esc(it.name)}</span></div>`;
  return `<div class="tv ip ${poster ? 'poster' : ''}" data-f data-ip="${d}" tabindex="-1"><div class="art ${it.img ? '' : 'icon'}">${it.img ? `<img class="cov" src="${esc(it.img)}" onerror="this.parentNode.classList.add('icon');this.remove()">` : ''}${!it.img ? `<b class="ipn">${esc(initials(it.name))}</b><em class="pt">${esc(it.name)}</em>` : ''}${it.prog ? `<div class="prog"><i style="width:${Math.round(it.prog * 100)}%"></i></div>` : ''}<i class="gloss"></i></div><span class="lbl">${esc(it.name)}</span></div>`;
}
function renderIptvRows() {
  $('#ipRecent').innerHTML = recent().slice(0, 12).map(x => ipCard(x)).join('');
  $('#rowRecent').classList.toggle('hidden', !recent().length);
  $('#ipLive').innerHTML = IP.live.slice(0, 20).map(x => ipCard(ipItem('live', x))).join('');
  $('#ipMovies').innerHTML = IP.vod.slice(0, 20).map(x => ipCard(ipItem('movie', x), true)).join('');
  $('#ipSeries').innerHTML = IP.series.slice(0, 20).map(x => ipCard(ipItem('series', x), true)).join('');
  if ($('#picks')) renderHub();
}
function playIp(it) {
  if (it.kind === 'series') { openHub(it); return; }
  const r = recent().filter(x => !(x.kind === it.kind && x.id === it.id)); r.unshift(Object.assign({}, it, { at: Date.now() })); cache('ip.recent', r.slice(0, 30));
  const ok = A.iptvPlay(it.kind, it.id, it.ext || '', it.name);
  toast(ok ? `▶ ${it.name}${A.liwaInstalled && A.liwaInstalled() ? ' — في Liwaiptv' : ''}` : 'تعذّر التشغيل — تحقق من الاشتراك');
  renderIptvRows();
}
/* «مختاراتي» hub — Apple-TV style: full-screen backdrop that follows focus */
function ensureHub() {
  if ($('#picks')) return;
  const s = document.createElement('section'); s.id = 'picks'; s.className = 'layer';
  s.innerHTML = `<div id="hbBack"><div class="hb on"></div><div class="hb"></div></div><div id="hbScrim"></div>
  <div id="hbHero"><div class="hk">مختاراتي · Liwaiptv</div><h1 id="hbTitle"></h1><div id="hbMeta"></div><p id="hbPlot"></p></div>
  <div id="hbRows" class="scroller"></div>`;
  document.body.insertBefore(s, $('#toast'));
}
let HBI = 0, HBT = null;
function hubBack(url) {
  const ls = $$('#hbBack .hb'), nx = ls[HBI ^ 1];
  const fall = PHOTOS.length ? `bg/${PHOTOS[Math.floor(Math.random() * PHOTOS.length)].file}` : '';
  nx.style.backgroundImage = `url("${url || fall}")`; nx.classList.toggle('tint', !url);
  nx.classList.add('on'); ls[HBI].classList.remove('on'); HBI ^= 1;
}
function hubFocus(it) {
  $('#hbTitle').textContent = it.name;
  $('#hbMeta').innerHTML = [{ live: 'بث مباشر', movie: 'فيلم', series: 'مسلسل' }[it.kind], it.year, it.rating ? '★ ' + (+it.rating).toFixed(1) : '', it.genre].filter(Boolean).map(esc).join(' · ');
  $('#hbPlot').textContent = it.plot || '';
  clearTimeout(HBT); HBT = setTimeout(() => hubBack(it.back || ''), 220);
}
function renderHub(series) {
  ensureHub();
  const rows = [];
  if (recent().length) rows.push(['متابعة المشاهدة', recent().slice(0, 15), false]);
  rows.push(['أفلام أضيفت حديثاً', IP.vod.slice(0, 24).map(x => ipItem('movie', x)), true]);
  rows.push(['مسلسلات', IP.series.slice(0, 24).map(x => ipItem('series', x)), true]);
  rows.push(['القنوات المباشرة', IP.live.slice(0, 30).map(x => ipItem('live', x)), false]);
  $('#hbRows').innerHTML = rows.filter(r => r[1].length).map(([t, l, p]) => `<section class="row"><h2>${t}</h2><div class="strip">${l.map(x => ipCard(x, p)).join('')}</div></section>`).join('');
}
async function openHub(series) {
  ensureHub(); renderHub(); open('picks');
  if (series) { // series → episodes of season 1 from get_series_info (real data only)
    try {
      const j = await ipApi('get_series_info', 'series_id=' + series.id); const eps = Object.values(j.episodes || {}).flat().slice(0, 40);
      const back = (j.info?.backdrop_path || [])[0];
      $('#hbRows').insertAdjacentHTML('afterbegin', `<section class="row"><h2>${esc(series.name)} — الحلقات</h2><div class="strip">${eps.map(e => ipCard({ kind: 'episode', id: String(e.id), ext: e.container_extension || 'mp4', name: `م${e.season} ح${e.episode_num} · ${e.title || ''}`, img: e.info?.movie_image || series.img, back: back || series.back, plot: e.info?.plot || j.info?.plot })).join('')}</div></section>`);
      cur = null; focusFirst();
    } catch (e) { toast('تعذّر جلب الحلقات'); }
  }
}

/* ════════ RAMADAN (computed on-device, Umm al-Qura) ════════ */
const isRamadan = () => { const h = hijri(); return h && h.m === 9; };
function renderRamadan() {
  const on = isRamadan() || pref('ramadan', '') === 'force';
  $('#ramCard').classList.toggle('hidden', !on);
  if (!on) return;
  const np = prayerDay(), now = Date.now(), ift = np[4], ims = np[0] - 10 * 60000;
  $('#ramCity').textContent = city().ar;
  $('#ramTx').innerHTML = now < ims ? `الإمساك ${fmtTime(ims)} · بعد ${untilText(ims - now)}` : now < ift ? `<b>الإفطار ${fmtTime(ift)}</b> · بعد ${untilText(ift - now)}` : `تقبّل الله صيامكم · سحور الغد قبل ${fmtTime(prayerDay(now + 864e5)[0] - 6e5)}`;
}
function renderImsakiya() {
  const h = hijri(); $('#ramHead').textContent = `${city().ar} · ${h ? h.y : ''} هـ`;
  let t = Date.now(); if (h && h.m === 9) t -= (h.d - 1) * 864e5; // from 1 Ramadan (approx. by Umm al-Qura)
  const rows = []; for (let i = 0; i < 30; i++) { const d = t + i * 864e5, p = prayerDay(d), hh = hijri(d); if (h && h.m === 9 && hh && hh.m !== 9) break;
    rows.push(`<tr><td>${hh ? hh.d + ' ' + HIJRI_M[hh.m - 1] : ''}</td><td>${dateLines(d).greg}</td><td>${fmtTime(p[0] - 6e5, false)}</td><td>${fmtTime(p[0], false)}</td><td>${fmtTime(p[2], false)}</td><td>${fmtTime(p[3], false)}</td><td><b>${fmtTime(p[4], false)}</b></td><td>${fmtTime(p[5], false)}</td></tr>`); }
  $('#ramTable').innerHTML = `<table class="imsak"><tr><th>هجري</th><th>ميلادي</th><th>الإمساك</th><th>الفجر</th><th>الظهر</th><th>العصر</th><th>المغرب (الإفطار)</th><th>العشاء</th></tr>${rows.join('')}</table>`;
}

/* ════════ WEATHER ALERTS (thresholds on Open-Meteo forecast) ════════ */
window.onWeather = () => {
  const out = []; if (!WX || !WX.daily) return;
  const d = WX.daily, i = 0;
  if (d.temperature_2m_max[i] >= 46) out.push(['🔥', `حرارة شديدة اليوم: ${Math.round(d.temperature_2m_max[i])}°`]);
  if ((d.wind_gusts_10m_max || [])[i] >= 55) out.push(['💨', `رياح قوية: هبات حتى ${Math.round(d.wind_gusts_10m_max[i])} كم/س`]);
  if ((d.precipitation_sum || [])[i] >= 5) out.push(['🌧', `أمطار متوقعة: ${d.precipitation_sum[i]} مم`]);
  if ((d.uv_index_max || [])[i] >= 11) out.push(['☀', `مؤشر الأشعة فوق البنفسجية مرتفع جداً (${Math.round(d.uv_index_max[i])})`]);
  const vis = (WX.hourly?.visibility || []).slice(0, 24); if (vis.length && Math.min(...vis) < 1000) out.push(['🌫', `احتمال ضباب: الرؤية قد تقل عن ${Math.round(Math.min(...vis))} م`]);
  $('#alerts').innerHTML = out.map(([i, t]) => `<div class="alert">${i} ${esc(t)} <small>· تقدير من Open-Meteo — راجع المركز الوطني للأرصاد للتحذيرات الرسمية</small></div>`).join('');
};

/* ════════ KIDS MODE ════════ */
const KIDS = () => pref('kids', 'off') !== 'off';
function kidsTick() {
  if (!KIDS()) { $('#kidsLock').classList.add('hidden'); return; }
  let st = +pref('kids.start', '0'); if (!st || Date.now() - st > 20 * 3600e3) { st = Date.now(); setPref('kids.start', String(st)); }
  const left = st + (+pref('kids')) * 60000 - Date.now();
  $('#kidsLock').classList.toggle('hidden', left > 0);
}
let backHold = null;
document.addEventListener('keydown', e => { if (!$('#kidsLock').classList.contains('hidden')) { e.stopImmediatePropagation(); e.preventDefault(); if ((e.key === 'Enter' || e.key === 'Escape' || e.key === 'Backspace') && !backHold) backHold = setTimeout(() => { setPref('kids', 'off'); setPref('kids.start', '0'); kidsTick(); applyKids(); toast('أُوقف وضع الأطفال'); }, 3000); } }, true);
document.addEventListener('keyup', () => { clearTimeout(backHold); backHold = null; }, true);
function applyKids() { document.body.classList.toggle('kids', KIDS()); renderNews(); loadIptv(); }

/* ════════ AI COMMAND LAYER (tool calling) ════════ */
const TOOLS = {
  open_app: 'افتح تطبيقاً مثبتاً {pkg}',
  open_section: 'افتح قسماً {id: prayers|weather|quran|news|briefing|assistant|settings|ambient|ramadan|picks|football|home}',
  play_iptv: 'شغّل من Liwaiptv {query, kind: live|movie|series}',
  news: 'اعرض الأخبار {cat: محلي|اقتصاد|رياضة|طقس|حكومة|عالمي|الكل}',
  quran: 'افتح سورة {sura: رقم 1-114, aya?: رقم}',
  set_pref: 'غيّر إعداداً {key: city|adhan|clock|idle|kids, value}',
  remind: 'تذكير بعد مدة {minutes, text}',
  briefing: 'الموجز اليومي مقروءاً {}',
  ask_screen: 'اشرح ما على الشاشة {question}',
  translate: 'ترجم نصاً عربي↔إنجليزي {text}',
  answer: 'أجب إجابة قصيرة من السياق فقط {text}',
};
function screenContext() {
  const L = layerEl(); const f = cur ? (cur.innerText || cur.value || '').trim().slice(0, 600) : '';
  let extra = '';
  if (cur && cur.dataset.nitem) { const n = NEWS[+cur.dataset.nitem]; extra = `خبر: ${n.title}\n${n.desc}\nالمصدر: ${n.src}`; }
  if (cur && cur.dataset.news) { const n = NEWS[+cur.dataset.news]; extra = `خبر: ${n.title}\n${n.desc}\nالمصدر: ${n.src}`; }
  if (stack[stack.length - 1] === 'explain') extra = $('#exVerse').textContent + '\n' + $('#exSrc').innerText;
  return `القسم: ${L.id}\nالعنصر المحدد: ${f}\n${extra}\nنص الشاشة: ${(L.innerText || '').replace(/\s+/g, ' ').slice(0, 1800)}`;
}
function localIntent(q) { // offline / no-key fallback: rule-based Arabic intents
  const t = q.trim(), low = t.toLowerCase();
  const app = APPS.find(a => low.includes(a.label.toLowerCase()) || a.label.length > 2 && t.includes(a.label));
  if (/افتح|شغل|شغّل|open/.test(t) && app) return { tool: 'open_app', args: { pkg: app.pkg } };
  const ch = [...IP.live, ...IP.vod].find(x => x.name && low.includes(x.name.toLowerCase().replace(/ hd$/, '')));
  if (ch) return { tool: 'play_iptv', args: { query: ch.name } };
  const sec = [['صلا|أذان|اذان', 'prayers'], ['طقس|جو', 'weather'], ['قرآن|قران|سورة', 'quran'], ['أخبار|اخبار|عاجل', 'news'], ['موجز', 'briefing'], ['إعداد|اعداد', 'settings'], ['رمضان|إمساك|امساك|إفطار|افطار', 'ramadan'], ['مختارات|أفلام|افلام|مسلسل', 'picks'], ['دوري|مباراة|مباريات', 'football'], ['الرئيسية|رجوع', 'home']];
  for (const [re, id] of sec) if (new RegExp(re).test(t)) return { tool: 'open_section', args: { id } };
  if (/ذكرني|ذكّرني/.test(t)) { const m = t.match(/(\d+)/); return { tool: 'remind', args: { minutes: m ? +m[1] : 10, text: t } }; }
  return null;
}
async function runCommand(q) {
  q = (q || '').trim(); if (!q) return;
  const out = $('#cmdOut'); out.innerHTML = '…';
  let call = null;
  if (aiReady() && online()) {
    try {
      call = await aiJSON(`أنت طبقة التحكم الذكية في تلفزيون «الكود TV». حوّل طلب المستخدم إلى استدعاء أداة واحدة: {"tool":"اسم","args":{...},"say":"جملة عربية قصيرة للمستخدم"}.
الأدوات: ${Object.entries(TOOLS).map(([k, v]) => k + ': ' + v).join('\n')}
التطبيقات المثبتة (اسم=حزمة): ${APPS.slice(0, 60).map(a => a.label + '=' + a.pkg).join('، ')}
${IP.live.length ? 'قنوات Liwaiptv أمثلة: ' + IP.live.slice(0, 40).map(x => x.name).join('، ') : 'لا يوجد اشتراك Liwaiptv'}
السياق:\n${contextBlock()}
لا تخترع حقائق؛ إن لم تكن المعلومة في السياق فاستخدم answer وقل إنك لا تعرف.`, q);
    } catch (e) { call = null; }
  }
  call = call && TOOLS[call.tool] ? call : (localIntent(q) || { tool: 'answer', args: { text: aiReady() ? 'لم أفهم الطلب، جرّب: «افتح يوتيوب» أو «أخبار الرياضة».' : 'الذكاء الاصطناعي غير متاح الآن (لا اتصال أو لا مفتاح). جرّب أوامر مثل «افتح يوتيوب» أو «الطقس».' } });
  return execTool(call, q);
}
async function execTool(c, q) {
  const a = c.args || {}, out = $('#cmdOut'), say = t => { out.innerHTML = (c.say !== undefined || c.ai ? AITAG + ' ' : '') + esc(t); };
  const close = () => setTimeout(closeCmd, 900);
  switch (c.tool) {
    case 'open_app': { const ap = APPS.find(x => x.pkg === a.pkg); if (!ap) return say('هذا التطبيق غير مثبت'); say('جارٍ فتح ' + ap.label); close(); return setTimeout(() => A.launch(ap.pkg), 500); }
    case 'open_section': { closeCmd(); if (a.id === 'home') return onHome(); if (a.id === 'picks') return openHub(); if (a.id === 'briefing') { open('briefing'); return briefing(); } if (a.id === 'news') { open('news'); return renderNewsPanel(); } if (a.id === 'ramadan') { open('ramadan'); return renderImsakiya(); } return open(a.id); }
    case 'play_iptv': { if (!hasIP()) return say('لا يوجد اشتراك Liwaiptv في الإعدادات'); const ql = String(a.query || '').toLowerCase();
      const pools = a.kind === 'movie' ? [['movie', IP.vod]] : a.kind === 'series' ? [['series', IP.series]] : [['live', IP.live], ['movie', IP.vod], ['series', IP.series]];
      for (const [k, l] of pools) { const x = l.find(x => x.name.toLowerCase().includes(ql)) || l.find(x => ql.split(/\s+/).every(w => x.name.toLowerCase().includes(w))); if (x) { say('▶ ' + x.name); close(); return playIp(ipItem(k, x)); } }
      return say(`لم أجد «${a.query}» في اشتراكك`); }
    case 'news': { closeCmd(); NCAT = NCATS.includes(a.cat) ? a.cat : 'الكل'; open('news'); return renderNewsPanel(); }
    case 'quran': { const n = Math.min(114, Math.max(1, +a.sura || 1)); closeCmd(); onHome(); await openSura(n); if (a.aya) { const v = $(`[data-aya="${+a.aya}"]`); if (v) { setFocus(v); v.scrollIntoView({ block: 'center' }); } } return; }
    case 'set_pref': { const ok = { city: CITIES.some(x => x.id === a.value), adhan: ['on', 'off'].includes(a.value), clock: ['12', '24'].includes(String(a.value)), idle: ['0', '5', '15'].includes(String(a.value)), kids: ['off', '30', '60', '120'].includes(String(a.value)) }[a.key];
      if (!ok) return say('لا يمكن تغيير هذا الإعداد بهذه القيمة'); setPref(a.key, String(a.value)); if (a.key === 'city') { loadWeather(); tick(); } if (a.key === 'kids') { setPref('kids.start', '0'); kidsTick(); applyKids(); } return say('تم ✓'); }
    case 'remind': { const m = Math.max(1, Math.min(600, +a.minutes || 10)); setTimeout(() => { toast('⏰ تذكير: ' + (a.text || '')); A.speak && A.speak('تذكير: ' + (a.text || '')); }, m * 60000); return say(`سأذكّرك بعد ${m} دقيقة (ما دام الكود TV مفتوحاً)`); }
    case 'briefing': { closeCmd(); open('briefing'); await briefing(); return; }
    case 'ask_screen': return askScreen(a.question || q);
    case 'translate': return translate(a.text || q);
    default: return say(a.text || c.say || '');
  }
}
async function askScreen(question) {
  const out = $('#cmdOut'); if (!aiReady()) { out.textContent = 'الذكاء الاصطناعي غير متاح الآن.'; return; }
  out.innerHTML = '…';
  try {
    const r = await ai('أجب عن سؤال المستخدم عمّا يظهر على شاشة التلفزيون الآن، بالعربية وفي 2-4 جمل، معتمداً فقط على نص الشاشة المعطى. إن تعلّق الأمر بآية فاعتمد على نص التفسير المعروض واذكر مصدره. لا تضف معلومات غير موجودة؛ إن لم تكفِ فقل ذلك.', [{ role: 'user', text: screenContext() + '\n\nالسؤال: ' + question }]);
    out.innerHTML = AITAG + ' ' + esc(r) + '<small class="srcl">مبني على محتوى الشاشة الحالية فقط</small>';
  } catch (e) { out.textContent = e.message; }
}
async function translate(text) {
  const out = $('#cmdOut'); if (!aiReady()) { out.textContent = 'الترجمة تحتاج اتصالاً ومفتاح ذكاء اصطناعي.'; return; }
  out.innerHTML = '…';
  try { const r = await ai('أنت مترجم. إن كان النص عربياً فترجمه إلى الإنجليزية، وإلا فإلى العربية. أعد الترجمة فقط بلا شرح.', [{ role: 'user', text: text.replace(/^ترجم[:\s]*/,'') }]); out.innerHTML = AITAG + ' ترجمة: <b dir="auto">' + esc(r) + '</b>'; A.speak && A.speak(r); } catch (e) { out.textContent = e.message; }
}
let CMD_MODE = '';
function openCmd(voice, mode = '') {
  CMD_MODE = mode; $('#cmd').classList.remove('hidden'); $('#cmdOut').innerHTML = mode === 'translate' ? 'اكتب أو قل النص المراد ترجمته (عربي ⇄ إنجليزي)' : 'أوامر: «افتح نتفليكس» · «شغّل قناة …» · «أخبار الاقتصاد» · «سورة الكهف» · «ما هذا؟» · «ذكرني بعد 20 دقيقة» · «غيّر المدينة إلى دبي»';
  const i = $('#cmdIn'); i.value = ''; setTimeout(() => i.focus(), 30);
  if (voice && A.hasVoice()) A.listen();
}
function closeCmd() { $('#cmd').classList.add('hidden'); $('#cmdIn').blur(); }
$('#cmdIn').addEventListener('keydown', e => {
  e.stopPropagation();
  if (e.key === 'Enter') { const v = e.target.value; e.target.value = ''; CMD_MODE === 'translate' ? translate(v) : /^(ما هذا|اشرح|وش هذا|ما الذي)/.test(v) ? askScreen(v) : runCommand(v); }
  if (e.key === 'Escape' || e.key === 'GoBack' || (e.key === 'Backspace' && !e.target.value)) closeCmd();
});
$('#cmdMic').onclick = () => A.hasVoice() ? A.listen() : toast('لا يدعم هذا الجهاز الإدخال الصوتي');
const _onVoice = window.onVoice;
window.onVoice = t => { if (!$('#cmd').classList.contains('hidden')) { if (t) { $('#cmdIn').value = t; CMD_MODE === 'translate' ? translate(t) : runCommand(t); } else toast('لم يتم التعرف على الصوت'); } else _onVoice(t); };
window.openAssistant = voice => openCmd(voice);   // remote mic / search key → global command bar
window.onBack = () => { if (!$('#cmd').classList.contains('hidden')) return closeCmd(); back(); };
document.addEventListener('keydown', e => { // menu key (outside app row) or "/" opens the AI bar from anywhere
  if (!$('#cmd').classList.contains('hidden')) { if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'GoBack') { closeCmd(); e.stopImmediatePropagation(); e.preventDefault(); } return; }
  if (e.key === '/' || (e.key === 'ContextMenu' && !(cur && cur.closest('#appRow')))) { openCmd(false); e.stopImmediatePropagation(); e.preventDefault(); }
}, true);

/* ════════ AI-PERSONALISED HOME ════════ */
function localRowOrder() {
  const h = uaeParts().h, np = nextPrayer(), soon = np.t - Date.now() < 45 * 60000;
  const base = h < 11 ? ['news', 'sug', 'iptvRecent', 'iptvLive', 'apps', 'iptvMovies', 'iptvSeries'] : h < 18 ? ['sug', 'news', 'iptvRecent', 'iptvLive', 'apps', 'iptvMovies', 'iptvSeries'] : ['iptvRecent', 'sug', 'iptvMovies', 'iptvSeries', 'iptvLive', 'news', 'apps'];
  return { order: base, why: soon ? `اقتربت صلاة ${PNAMES[np.i]}` : '' };
}
async function personalise() {
  if (pref('ai.home', 'on') === 'off') return;
  let o = localRowOrder();
  const key = 'home.' + uaeParts().d + '.' + slot();
  const c = cache(key);
  if (c) o = c; else if (aiReady() && online()) {
    try {
      let U = {}; try { U = JSON.parse(A.usage()); } catch { }
      const j = await aiJSON('رتّب صفوف الشاشة الرئيسية لتلفزيون أسرة إماراتية حسب الوقت ومواقيت الصلاة وسجل الاستخدام. أعد {"order":[معرّفات الصفوف كلها],"why":"سبب قصير جداً"}. المعرّفات: sug,news,apps,iptvRecent,iptvLive,iptvMovies,iptvSeries', { time: fmtTime(Date.now()), weekday: DAYS[uaeParts().wd], next_prayer: PNAMES[nextPrayer().i] + ' ' + fmtTime(nextPrayer().t), ramadan: isRamadan(), has_iptv: hasIP(), top_apps: Object.entries(U).sort((a, b) => (b[1].total || 0) - (a[1].total || 0)).slice(0, 6).map(([p, u]) => [APPS.find(a => a.pkg === p)?.label || p, u.total]) });
      if (Array.isArray(j.order)) { o = { order: j.order, why: '✦ ' + (j.why || ''), ai: true }; cache(key, o); }
    } catch { }
  }
  const sc = $('#home .scroller') || $('#home'); const rows = Object.fromEntries($$('#home .row[data-row]').map(r => [r.dataset.row, r]));
  const anchor = $('#home .row[data-row]'); const parent = anchor.parentNode;
  o = Object.assign({}, o, { order: ['fav'].concat((o.order || []).filter(x => x !== 'fav')) });
  const seen = new Set(); o.order.filter(id => rows[id] && !seen.has(id) && seen.add(id)).concat(Object.keys(rows).filter(k => !seen.has(k))).forEach(id => parent.insertBefore(rows[id], $('#credit')));
  if (o.why) { const g = $('#greet'); g.dataset.why = o.why; }
}
async function greet() {
  const key = 'greet.' + uaeParts().d + '.' + slot(); let g = cache(key);
  if (!g && aiReady() && online()) { try { g = (await ai('اكتب تحية افتتاحية واحدة دافئة بلهجة إماراتية مهذبة (حتى 14 كلمة) تناسب الوقت، تذكر الطقس أو الصلاة القادمة من السياق فقط. بلا رموز تعبيرية.', [{ role: 'user', text: contextBlock() }])).trim(); cache(key, g); } catch { } }
  if (!g) return;
  $('#greetTx').textContent = g; $('#greet').classList.remove('hidden');
}
// briefing: narrate automatically via TTS when enabled
const _brief = briefing;
window.briefing = briefing = async function () { await _brief(); $('#briefSrc').insertAdjacentHTML('afterbegin', AITAG + ' '); if (pref('brief.speak', 'on') === 'on' && aiReady()) A.speak && A.speak($('#briefBody').textContent); };

/* ════════ activation hooks ════════ */
const _activate = activate;
window.activate = activate = function (el) {
  if (!el) return;
  if (el.dataset.ip) return playIp(JSON.parse(el.dataset.ip));
  if (el.dataset.news) { open('news'); renderNewsPanel(); const t = $(`[data-nitem="${el.dataset.news}"]`); if (t) setTimeout(() => setFocus(t), 60); return; }
  if (el.dataset.nitem) { const n = NEWS[+el.dataset.nitem]; A.speak && A.speak(n.title + '. ' + (n.sum || '')); toast('المصدر: ' + n.src + ' — ' + (n.link || '')); return; }
  if (el.dataset.ncat) { NCAT = el.dataset.ncat; renderNewsPanel(); setFocus($(`[data-ncat="${NCAT}"]`)); return; }
  const act = el.dataset.act;
  if (act === 'news') { open('news'); return renderNewsPanel(); }
  if (act === 'ramadan') { open('ramadan'); return renderImsakiya(); }
  if (act === 'translate') return openCmd(false, 'translate');
  if (act === 'assistant') return openCmd(false);
  if (act === 'picks') return openHub();
  if (act === 'kids') { open('settings'); setTimeout(() => setFocus($('[data-pref="kids"][data-val="60"]')), 80); return; }
  if (el.dataset.pref === 'kids') { setPref('kids.start', '0'); _activate(el); kidsTick(); applyKids(); return; }
  return _activate(el);
};
// hub backdrop follows focus
const _setFocus = setFocus;
window.setFocus = setFocus = function (el) { _setFocus(el); if (el && el.dataset.ip && stack[stack.length - 1] === 'picks') hubFocus(JSON.parse(el.dataset.ip)); };
// settings: Liwaiptv subscription (encrypted natively; never shown back)
const _rs = renderSettings;
window.renderSettings = renderSettings = function () {
  _rs(); $$('[data-pref="kids"],[data-pref="ai.home"],[data-pref="brief.speak"]').forEach(b => b.classList.toggle('sel', String(pref(b.dataset.pref, { kids: 'off', 'ai.home': 'on', 'brief.speak': 'on' }[b.dataset.pref])) === b.dataset.val));
  $('#ipInfo').textContent = hasIP() ? 'محفوظ ومشفّر على الجهاز ✓' : (A.liwaInstalled && A.liwaInstalled() ? 'Liwaiptv مثبت — أدخل بيانات الاشتراك مرة واحدة' : 'غير مضبوط');
};
$('#ipSave').onclick = () => { const s = $('#ipS').value.trim(), u = $('#ipU').value.trim(), p = $('#ipP').value; if (!/^https?:\/\//.test(s) || !u || !p) return toast('أدخل الخادم واسم المستخدم وكلمة المرور'); A.iptvSet(s, u, p); $('#ipS').value = $('#ipU').value = $('#ipP').value = ''; renderSettings(); toast('حُفظ الاشتراك مشفّراً'); loadIptv(); };
$('#ipClear').onclick = () => { A.iptvSet('', '', ''); renderSettings(); loadIptv(); toast('حُذف الاشتراك'); };
// a «مختاراتي» tile in sections when a subscription exists
(function addHubTile() { return; const t = document.createElement('div'); t.className = 'tv sec'; t.id = 'secPicks'; t.dataset.f = ''; t.dataset.act = 'picks'; t.tabIndex = -1; t.innerHTML = '<div class="art g-pck"><b>🎬</b><i class="gloss"></i></div><span class="lbl">مختاراتي</span>'; $('#secRow').prepend(t); })();

/* icons: Streamline «Flex Line – Free» (see CREDITS.md) used as CSS masks so they take the text colour */
const ICONSET = ['picks', 'news', 'prayers', 'quran', 'weather', 'assistant', 'briefing', 'football', 'ambient', 'translate', 'settings', 'kids'];
const ICON = (a, fb) => ICONSET.includes(a) ? `<i class="ico" style="--ico:url(icons/${a}.svg)"></i>` : `<i>${fb}</i>`;
/* ════════ tvOS-style top tab bar (sections + gear) ════════ */
(function tabBar() {
  const items = [['picks', '', 'مختاراتي'], ['news', '', 'الأخبار'], ['prayers', '', 'الصلاة'], ['quran', '', 'القرآن'], ['weather', '', 'الطقس'], ['football', '', 'الدوري'], ['assistant', '', 'اسأل ✦']];
  const nav = document.createElement('nav'); nav.id = 'tabs';
  nav.innerHTML = '<div class="tabs">' + items.map(([a, i, t]) => `<button class="tab${a === 'picks' || a === 'football' ? ' hidden' : ''}" ${a === 'picks' ? 'id="tabPicks"' : a === 'football' ? 'id="secFoot"' : ''} data-f data-act="${a}" tabindex="-1">${ICON(a, i)}<span>${t}</span></button>`).join('') + '</div><button class="tab gear" data-f data-act="settings" tabindex="-1" title="الإعدادات">' + ICON('settings', '⚙') + '</button>';
  $('#home').prepend(nav);
  const sec = $('[data-row="sec"]'); if (sec) sec.remove();
})();

/* ════════ boot ════════ */
(function boot2() {
  if (!A || IS_DREAM) return;
  const sync = () => $('#tabPicks').classList.toggle('hidden', !hasIP() || KIDS());
  sync(); kidsTick(); setInterval(kidsTick, 30000); document.body.classList.toggle('kids', KIDS());
  loadNews(); setInterval(loadNews, 15 * 60000);
  loadIptv().then(sync).then(personalise);
  renderRamadan(); setInterval(renderRamadan, 30000);
  setTimeout(() => { greet(); personalise(); }, 1500);
})();
