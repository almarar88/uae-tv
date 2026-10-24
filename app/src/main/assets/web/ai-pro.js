/* الكود 1.2 — proactive AI layer. Rules: never invent facts; everything AI-made carries ✦; works offline with local fallbacks. */
'use strict';
const WIKI_TAG = '<span class="aitag wk">ويكيبيديا</span>';

/* ── 1. title cleaning + Wikipedia/Wikidata artwork enrichment ───────── */
function cleanLocal(name) { // deterministic first pass: strip IPTV noise like "AR | ", "[4K]", "(2019) FHD"
  let t = String(name || '').replace(/^[A-Z]{2,3}\s*[|:\-–]\s*/i, '').replace(/[\[(]?\b(4K|UHD|FHD|HD|SD|HEVC|H265|MULTI|SUB|DUB|VIP)\b[\])]?/gi, '').replace(/[|★]+/g, ' ');
  const y = (t.match(/[(\[]?\b(19[3-9]\d|20[0-4]\d)\b[)\]]?/) || [])[1];
  if (y) t = t.replace(new RegExp('[(\\[]?\\b' + y + '\\b[)\\]]?'), '');
  return { title: t.replace(/\s{2,}/g, ' ').replace(/[\s\-–:|]+$/, '').trim(), year: y || '' };
}
async function cleanTitles(items) { // ✦ Gemini canonicalises the messy ones, in small batches (token limit)
  const C = cache('art.titles') || {}, todo = items.filter(x => !C[x.name]).slice(0, 10);
  todo.forEach(x => { C[x.name] = Object.assign(cleanLocal(x.name), { src: 'local' }); });
  if (todo.length && aiReady() && online()) {
    try {
      const r = await aiJSON('نظّف عناوين أفلام/مسلسلات IPTV إلى العنوان الأصلي المعروف والسنة إن كانت ظاهرة في النص فقط. لا تخمّن سنة غير موجودة. أعد {"items":[{"i":رقم,"title":"...","year":"" }]}',
        todo.map((x, i) => i + ': ' + x.name).join('\n'));
      (r.items || []).forEach(o => { const x = todo[o.i]; if (x && o.title) C[x.name] = { title: String(o.title).slice(0, 80), year: /^\d{4}$/.test(o.year) ? o.year : C[x.name].year, src: 'ai' }; });
    } catch (e) { }
  }
  cache('art.titles', C); return C;
}
const WK = (lang, q) => raw(`https://${lang}.wikipedia.org/w/api.php?action=query&format=json&origin=*&list=search&srlimit=3&srsearch=${encodeURIComponent(q)}`).then(JSON.parse);
const WKS = (lang, t) => raw(`https://${lang}.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(t.replace(/ /g, '_'))}`).then(JSON.parse);
function confident(s, ct, kind) { // only accept a page that is clearly the same work
  const norm = x => String(x || '').toLowerCase().replace(/\(.*?\)/g, '').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
  const d = (s.description || '') + ' ' + (s.extract || '').slice(0, 200);
  const typeOk = kind === 'series' ? /television series|TV series|مسلسل|سلسلة تلفزيونية|miniseries/i.test(d) : /film|فيلم|movie/i.test(d);
  const titleOk = norm(s.title) === norm(ct.title);
  const yearOk = !ct.year || d.includes(ct.year) || String(s.title).includes(ct.year);
  return s.type === 'standard' && typeOk && titleOk && yearOk;
}
async function enrich(it) {
  if (!it || (it.kind !== 'movie' && it.kind !== 'series') || !online()) return null;
  const E = cache('art.enrich') || {}; if (it.name in E) return E[it.name];
  const ct = (await cleanTitles([it]))[it.name]; let hit = null;
  try {
    for (const lang of /[\u0600-\u06FF]/.test(ct.title) ? ['ar', 'en'] : ['en', 'ar']) {
      const q = ct.title + (ct.year ? ' ' + ct.year : '') + (it.kind === 'series' ? (lang === 'ar' ? ' مسلسل' : ' TV series') : (lang === 'ar' ? ' فيلم' : ' film'));
      const res = (await WK(lang, q)).query?.search || [];
      for (const r of res.slice(0, 3)) { const s = await WKS(lang, r.title); if (confident(s, ct, it.kind)) { hit = { title: s.title, img: s.originalimage?.source || s.thumbnail?.source || '', plot: s.extract || '', url: s.content_urls?.desktop?.page || '', lang, wikidata: s.wikibase_item || '' }; break; } }
      if (hit) break;
    }
  } catch (e) { return null; } // offline / error → don't cache, try later
  E[it.name] = hit; cache('art.enrich', E); return hit;
}
/* hub hero: real provider data first, then a *confident* Wikipedia match, else nothing */
const _hubFocus = hubFocus;
window.hubFocus = hubFocus = function (it) {
  _hubFocus(it); const tok = it.kind + it.id; hubFocus.tok = tok;
  if (it.plot && it.back) return;
  enrich(it).then(w => {
    if (!w || hubFocus.tok !== tok) return;
    if (!it.plot && w.plot) $('#hbPlot').innerHTML = esc(w.plot.slice(0, 280)) + ' ' + WIKI_TAG;
    if (!it.back && w.img) hubBack(w.img);
  });
};
async function enrichPosters(root) { // fill missing posters only
  for (const el of [...root.querySelectorAll('.tv.ip .art.icon')].slice(0, 6)) {
    const it = JSON.parse(el.parentNode.dataset.ip || '{}'); const w = await enrich(it);
    if (w && w.img && el.isConnected) { el.classList.remove('icon'); el.insertAdjacentHTML('afterbegin', `<img class="cov" src="${esc(w.img)}">`); el.querySelector('.pt')?.remove(); el.querySelector('.ipn')?.remove(); el.insertAdjacentHTML('beforeend', '<i class="wkb" title="Wikipedia">W</i>'); }
  }
}

/* ── 2. channel auto-grouping + HD/SD dedupe ───────── */
const GROUPS = ['الإمارات', 'رياضة', 'أخبار', 'أطفال', 'أفلام', 'منوعات', 'دينية', 'وثائقية'];
const GRULES = [[/\b(abu ?dhabi|dubai|sharjah|ajman|emarat|al ?emarat|sama dubai|noor dubai|yas|ad sports|ras al ?khaimah|uae)\b|أبوظبي|دبي|الشارقة|الإمارات|عجمان/i, 'الإمارات'],
  [/\b(bein|ssc|sport|sports|espn|eurosport|alkass|al kass|f1)\b|رياض/i, 'رياضة'], [/\b(news|cnn|bbc|sky news|al ?arabiya|al ?jazeera|asharq|france 24|rt arabic)\b|أخبار|العربية|الجزيرة/i, 'أخبار'],
  [/\b(kids|cartoon|spacetoon|majid|nickelodeon|disney jr|cbeebies|baraem|jeem)\b|أطفال|ماجد|سبيستون/i, 'أطفال'], [/\b(movie|movies|cinema|film|rotana cinema|mbc ?2|mbc max|osn movies)\b|أفلام|سينما/i, 'أفلام'],
  [/\b(quran|sunnah|iqraa|makkah|madinah)\b|قرآن|القرآن|السنة|مكة/i, 'دينية'], [/\b(nat ?geo|discovery|documentary|history)\b|وثائق/i, 'وثائقية'], [/\b(mbc|rotana|lbc|mtv|dubai one|osn)\b/i, 'منوعات']];
const quality = n => /\b(4K|UHD)\b/i.test(n) ? 4 : /\bFHD\b/i.test(n) ? 3 : /\bHD\b/i.test(n) ? 2 : /\bSD\b/i.test(n) ? 0 : 1;
const baseName = n => String(n).replace(/^[A-Z]{2,3}\s*[|:]\s*/i, '').replace(/\b(4K|UHD|FHD|HD|SD|HEVC|H265|\+1)\b/gi, '').replace(/[\s\-|]+$/g, '').replace(/\s+/g, ' ').trim().toLowerCase();
function dedupe(list) { const m = new Map(); for (const x of list) { const k = baseName(x.name); const o = m.get(k); if (!o) m.set(k, Object.assign({}, x, { variants: [x] })); else { o.variants.push(x); if (quality(x.name) > quality(o.name)) Object.assign(o, x, { variants: o.variants }); } } return [...m.values()]; }
async function groupChannels(list) {
  const G = cache('ch.groups') || {}, todo = [];
  for (const x of list) { const k = baseName(x.name); if (G[k]) continue; const r = GRULES.find(([re]) => re.test(x.name)); if (r) G[k] = { g: r[1], src: 'rule' }; else todo.push(k); }
  if (todo.length && aiReady() && online()) {
    for (let i = 0; i < Math.min(todo.length, 120); i += 40) {
      const b = todo.slice(i, i + 40);
      try { const r = await aiJSON(`صنّف أسماء قنوات تلفزيونية إلى إحدى الفئات فقط: ${GROUPS.join('، ')}. إن لم تكن متأكداً فاختر «منوعات». أعد {"items":[{"i":رقم,"g":"فئة"}]}`, b.map((n, j) => j + ': ' + n).join('\n'));
        (r.items || []).forEach(o => { if (b[o.i] && GROUPS.includes(o.g)) G[b[o.i]] = { g: o.g, src: 'ai' }; }); } catch (e) { break; }
    }
  }
  cache('ch.groups', G);
  const out = {}; for (const x of dedupe(list)) { const g = (G[baseName(x.name)] || { g: 'منوعات', src: 'none' }); (out[g.g] = out[g.g] || { ai: false, items: [] }).items.push(x); if (g.src === 'ai') out[g.g].ai = true; }
  return out;
}
let CHG = null;
async function renderGroups() {
  if (!IP.live.length) return;
  CHG = await groupChannels(IP.live);
  const all = dedupe(IP.live), saved = IP.live.length - all.length;
  $('#ipLive').innerHTML = all.slice(0, 20).map(x => ipCard(ipItem('live', x))).join('');
  const h = $('#rowLive h2'); if (h && saved > 0) h.innerHTML = h.innerHTML.replace(/<small.*$/, '') + ` <small>دُمجت ${arCount(saved, "نسخة واحدة", "نسختان", "نسخ", "نسخة")} HD/SD</small>`;
  if ($('#hbRows')) {
    const html = GROUPS.filter(g => CHG[g]).map(g => `<section class="row"><h2>${g} ${CHG[g].ai ? '<span class="aitag">✦ تصنيف ذكي</span>' : ''}</h2><div class="strip">${CHG[g].items.slice(0, 24).map(x => ipCard(ipItem('live', x))).join('')}</div></section>`).join('');
    const live = [...$$('#hbRows .row h2')].find(h => h.textContent.startsWith('القنوات المباشرة')); if (live) live.parentNode.outerHTML = html;
  }
}
const _rir = renderIptvRows;
window.renderIptvRows = renderIptvRows = function () { _rir(); renderGroups().catch(() => { }); if ($('#hbRows')) enrichPosters($('#hbRows')).catch(() => { }); };

/* ── 3. «ماذا أشاهد الآن» — Top Shelf hero picks with a one-line reason ───────── */
function candidates() {
  const c = [], h = new Date().getHours(), np = (() => { try { return nextPrayer(); } catch { return null; } })();
  if (np && np.t - Date.now() < 25 * 60000) c.push({ type: 'prayer', id: 'prayers', title: `${PNAMES[np.i]} ${untilText(np.t - Date.now())}`, why: 'الأذان قريب' });
  if (FOOT && FOOT.today && FOOT.today.length) FOOT.today.slice(0, 2).forEach(e => c.push({ type: 'match', id: 'football', title: `${e.strHomeTeam} × ${e.strAwayTeam}`, why: 'مباراة اليوم في دوري المحترفين' }));
  recent().slice(0, 3).forEach(x => c.push({ type: 'ip', it: x, id: x.kind + x.id, title: x.name, img: x.back || '', why: 'تابعته مؤخراً' }));
  IP.vod.slice(0, 4).forEach(x => { const it = ipItem('movie', x); c.push({ type: 'ip', it, id: 'movie' + it.id, title: it.name, img: it.back || '', why: 'أضيف حديثاً إلى اشتراكك' }); });
  IP.series.slice(0, 3).forEach(x => { const it = ipItem('series', x); c.push({ type: 'ip', it, id: 'series' + it.id, title: it.name, img: it.back || '', plot: it.plot, why: 'مسلسل محدّث' }); });
  if (CHG && CHG['أخبار'] && h >= 18 && h <= 22) { const x = CHG['أخبار'].items[0]; const it = ipItem('live', x); c.push({ type: 'ip', it, id: 'live' + it.id, title: it.name, img: '', why: 'نشرة المساء' }); }
  // 1.3.1: news never appears on Home (it has its own screen)
  APPS.slice(0, 2).forEach(a => c.push({ type: 'app', id: a.pkg, title: a.label, img: '', why: 'من تطبيقاتك المفضّلة' })); // never a wordmark as hero art
  return c;
}
let PICKS = [];
async function whatToWatch() {
  const c = candidates(); if (!c.length) return;
  const key = 'w2w.' + new Date().toISOString().slice(0, 13); let sel = cache(key);
  if (!sel && aiReady() && online()) {
    try { const r = await aiJSON(`اختر حتى 4 عناصر من القائمة تناسب المشاهد الآن (الوقت ${new Date().toLocaleTimeString('ar-AE', { hour: '2-digit', minute: '2-digit' })}، ${KIDS() ? 'وضع الأطفال مفعّل' : ''}). لا تكتب أي نص. أعد {"picks":[{"i":رقم}]}`,
        c.map((x, i) => `${i}: [${x.type}] ${x.title} — ${x.why}`).join('\n'));
      sel = (r.picks || []).filter(p => c[+p.i]).slice(0, 4).map(p => ({ id: c[+p.i].id })); /* the model only chooses; reasons stay the fixed, reviewed ones */ if (sel.length) cache(key, sel); } catch (e) { sel = null; }
  }
  PICKS = sel ? sel.map(s => { const x = c.find(y => y.id === s.id); return x && Object.assign({}, x, { ai: true }); }).filter(Boolean) : c.slice(0, 4);
  if (!PICKS.length) PICKS = c.slice(0, 4);
  window.renderShelf && renderShelf();
}
function openPick(p) {
  if (!p) return; if (p.type === 'ip') return p.it.kind === 'series' ? openHub(p.it) : playIp(p.it);
  if (p.type === 'app') return A.launch(p.id); if (p.type === 'news') { open('news'); return renderNewsPanel(); }
  return open(p.id);
}

/* ── 4. natural-language search across apps, channels, movies, Quran, news ───────── */
const norm = s => String(s || '').toLowerCase().replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي').replace(/[\u064B-\u0652]/g, '');
function localSearch(words, kinds) {
  const W = words.map(norm).filter(w => w.length > 1); if (!W.length) return [];
  const sc = t => { const n = norm(t); return W.reduce((s, w) => s + (n.includes(w) ? (n.startsWith(w) ? 3 : 2) : 0), 0); };
  const out = [], push = (k, title, sub, data, s) => { if (s > 0) out.push({ k, title, sub, data, s }); };
  const want = k => !kinds || !kinds.length || kinds.includes(k);
  if (want('app')) APPS.forEach(a => push('app', a.label, 'تطبيق', { pkg: a.pkg }, sc(a.label)));
  if (want('live')) dedupe(IP.live).forEach(x => { const g = ((cache('ch.groups') || {})[baseName(x.name)] || {}).g || ''; push('live', x.name, 'قناة مباشرة' + (g ? ' · ' + g : ''), { ip: ipItem('live', x) }, sc(x.name + ' ' + g + (g === 'أخبار' ? ' اخبار news' : ''))); });
  if (want('movie')) IP.vod.forEach(x => push('movie', x.name, 'فيلم', { ip: ipItem('movie', x) }, sc(x.name)));
  if (want('series')) IP.series.forEach(x => push('series', x.name, 'مسلسل', { ip: ipItem('series', x) }, sc(x.name + ' ' + (x.genre || ''))));
  if (want('quran')) (SURAS.length ? SURAS : (window.SURAS_DATA || [])).forEach(s => push('quran', 'سورة ' + s.name, `${s.type} · ${arCount(s.ayas, "آية واحدة", "آيتان", "آيات", "آية")}`, { sura: s.n }, sc('سورة ' + s.name)));
  if (want('news')) NEWS.forEach((n, i) => push('news', n.title, n.src + ' · ' + (n.cat || ''), { news: i }, sc(n.title + ' ' + (n.cat || '') + ' ' + (n.sum || ''))));
  return out.sort((a, b) => b.s - a.s).slice(0, 18);
}
async function nlSearch(q) {
  let words = q.split(/\s+/), kinds = null, ai = false;
  if (aiReady() && online()) {
    try { const r = await aiJSON('حوّل طلب بحث تلفزيوني إلى كلمات مفتاحية (عربية وإنجليزية إن لزم، أسماء قنوات/أفلام/سور) وأنواع مطلوبة من: app, live, movie, series, quran, news. لا تضف عناوين غير مذكورة ضمنياً. أعد {"keywords":[...],"kinds":[...]}', q);
      if (Array.isArray(r.keywords) && r.keywords.length) { words = r.keywords.map(String).concat(words); ai = true; } if (Array.isArray(r.kinds)) kinds = r.kinds.filter(k => ['app', 'live', 'movie', 'series', 'quran', 'news'].includes(k)); } catch (e) { }
  }
  let res = localSearch(words, kinds); if (!res.length && kinds) res = localSearch(words, null);
  const out = $('#cmdOut');
  if (!res.length) { out.innerHTML = `لا نتائج لـ «${esc(q)}»`; return; }
  out.innerHTML = (ai ? AITAG + ' فهمت: ' + esc(words.slice(0, 4).join('، ')) : 'نتائج البحث') + `<div class="sres">${res.map((r, i) => `<div class="sr" data-f tabindex="-1" data-sr="${i}"><em>${{ app: '▢', live: '◉', movie: '▶', series: '▤', quran: '۞', news: '¶' }[r.k]}</em><b>${esc(r.title)}</b><small>${esc(r.sub)}</small></div>`).join('')}</div>`;
  nlSearch.res = res; cur = null; setFocus($('.sr'));
}
TOOLS.search = 'ابحث بلغة طبيعية في التطبيقات والقنوات والأفلام والمسلسلات والقرآن والأخبار {query}';
const _exec = execTool;
window.execTool = execTool = async function (c, q) { if (c.tool === 'search') return nlSearch(c.args?.query || q); return _exec(c, q); };
const _li = localIntent;
window.localIntent = localIntent = function (q) { if (/^(ابحث|بحث|دور|دوّر|search)\b/.test(q.trim())) return { tool: 'search', args: { query: q.replace(/^(ابحث عن|ابحث|بحث|دور على|دوّر على|search)\s*/, '') } }; return _li(q) || { tool: 'search', args: { query: q } }; };
document.addEventListener('keydown', e => { if (e.key !== 'Enter' || !cur || cur.dataset.sr === undefined) return; e.stopImmediatePropagation(); e.preventDefault();
  const r = nlSearch.res[+cur.dataset.sr]; closeCmd(); if (r.data.pkg) return A.launch(r.data.pkg); if (r.data.ip) return playIp(r.data.ip); if (r.data.sura) { onHome(); return openSura(r.data.sura); } if (r.data.news !== undefined) { open('news'); renderNewsPanel(); } }, true);

/* ── 5. proactive helpers ───────── */
const PRO = { said: cache('pro.said') || {} };
const once = (k, f) => { if (PRO.said[k]) return; PRO.said[k] = Date.now(); cache('pro.said', PRO.said); f(); };
function nudge(text, actLabel, fn) { // a small dismissible card; never acts on its own
  let n = $('#nudge'); if (!n) { n = document.createElement('div'); n.id = 'nudge'; document.body.appendChild(n); }
  n.innerHTML = `<span class="aitag">✦ اقتراح</span><p>${esc(text)}</p>${actLabel ? `<button data-f tabindex="-1" id="nudgeGo">${esc(actLabel)}</button>` : ''}`;
  n.className = 'on'; n.onclick = () => { fn && fn(); n.className = ''; }; clearTimeout(nudge.t); nudge.t = setTimeout(() => n.className = '', 15000);
}
function proactive() {
  const now = Date.now(), day = new Date().toDateString();
  try { const np = nextPrayer(), m = (np.t - now) / 60000;
    if (np.i !== 1 && m > 0 && m <= 5) once(`pray.${day}.${np.i}`, () => nudge(`أذان ${PNAMES[np.i]} بعد ${Math.ceil(m)} دقائق — هل تريد إيقاف المشاهدة مؤقتاً؟`, 'أوقف ▶ وأظهر الأوقات', () => { try { A.mediaPause && A.mediaPause(); } catch (e) { } open('prayers'); })); } catch (e) { }
  const bed = pref('kids.bed', 'off'); // e.g. "20:30"
  if (bed !== 'off' && !KIDS()) { const [H, M] = bed.split(':').map(Number), t = new Date(); t.setHours(H, M, 0, 0);
    if (now >= t && now - t < 60 * 60000) once('bed.' + day, () => { setPref('kids', '30'); setPref('kids.start', String(now)); kidsTick(); applyKids(); nudge('حان وقت نوم الأطفال — فُعّل وضع الأطفال تلقائياً (30 دقيقة).', 'حسناً'); }); }
  if (FOOT && FOOT.today) FOOT.today.forEach(e => { const t = evTime(e), m = (t - now) / 60000;
    if (m > 0 && m <= 10) once('match.' + e.idEvent, () => nudge(`تبدأ ${e.strHomeTeam} × ${e.strAwayTeam} بعد ${Math.ceil(m)} دقائق`, 'افتح الدوري', () => open('football'))); });
}
const _rs2 = renderSettings;
window.renderSettings = renderSettings = function () { _rs2(); $('#bedRow')?.remove();
  const v = pref('kids.bed', 'off'), opts = ['off', '19:30', '20:00', '20:30', '21:00'];
  $('#settings .setgrid').insertAdjacentHTML('beforeend', `<div class="set" id="bedRow"><label>✦ وضع الأطفال تلقائياً وقت النوم</label><div class="opts">${opts.map(o => `<button data-f tabindex="-1" data-pref="kids.bed" data-val="${o}" class="${o === v ? 'sel' : ''}">${o === 'off' ? 'إيقاف' : o}</button>`).join('')}</div></div>`); };

(function bootPro() {
  if (!A || IS_DREAM) return;
  setTimeout(whatToWatch, 2500); setInterval(whatToWatch, 20 * 60000);
  setInterval(proactive, 30000); setTimeout(proactive, 4000);
})();
