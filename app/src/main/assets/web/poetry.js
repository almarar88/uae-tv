/* ════════ الشعر — Emirati poetry (1.3.1) ════════
   Every verse comes from data/poetry.json (built by tools/poetry/build.py from fetched sources,
   verbatim). Nothing here is generated: no AI text, no verses typed by hand.
   1.3.1: the data is loaded lazily after first paint (not on the boot path); a newer copy of the same
   file is fetched from the repo at most once a day, validated, cached, and the bundled copy is the fallback. */
(function poetryLoader() {
  const REMOTE = 'https://raw.githubusercontent.com/almarar88/uae-tv/main/app/src/main/assets/web/data/poetry.json';
  const okStr = h => typeof h === 'string' && h.trim() && h.length < 300;
  const valid = d => !!d && Array.isArray(d.poets) && d.poets.length > 0 && d.poets.length < 100 && d.poets.every(p => p && okStr(p.name) && okStr(p.short) && Array.isArray(p.poems) && p.poems.length &&
    p.poems.every(q => q && okStr(q.title) && okStr(q.source) && Array.isArray(q.verses) && q.verses.length && q.verses.every(v => Array.isArray(v) && (v.length === 1 || v.length === 2) && v.every(okStr)) &&
      (q.wisdom || []).every(w => Array.isArray(w) && w.every(k => Number.isInteger(k) && k >= 0 && k < q.verses.length))));
  const newer = (a, b) => !b || String(a.built || '') > String(b.built || '') || (String(a.built || '') === String(b.built || '') && (a.version || 0) > (b.version || 0));
  window.loadPoetry = function (done) {
    let cached = null; try { cached = JSON.parse(localStorage['poetry.cache'] || 'null'); if (!valid(cached)) cached = null; } catch { cached = null; }
    const use = bundled => { const d = cached && (!valid(bundled) || newer(cached, bundled)) ? cached : bundled; done(valid(d) ? d : null); refresh(d); };
    if (window.POETRY_DATA) return use(window.POETRY_DATA);
    const s = document.createElement('script'); s.src = 'data/poetry.js'; s.onload = () => use(window.POETRY_DATA); s.onerror = () => use(null); document.head.appendChild(s);
  };
  function refresh(cur) {
    const last = +localStorage['poetry.ts'] || 0; if (Date.now() - last < 864e5 || !window.call) return;
    localStorage['poetry.ts'] = Date.now(); // at most one attempt per day, even if it fails
    call('get', REMOTE).then(t => { const d = JSON.parse(t); if (valid(d) && (!cur || newer(d, cur) || JSON.stringify(d).length !== JSON.stringify(cur).length)) localStorage['poetry.cache'] = JSON.stringify(d); }).catch(() => { });
  }
})();
(function poetry() {
  let D = null, P = [];
  const host = u => { try { return new URL(u).hostname.replace(/^www\./, ''); } catch { return ''; } };
  const srcLine = (name, url) => name ? `المصدر: ${esc(name)}${url ? ` <span dir="ltr">${esc(host(url))}</span>` : ''}` : '';
  const bayt = (v, cls = '') => v.length === 2 ? `<div class="by ${cls}"><span>${esc(v[0])}</span><span>${esc(v[1])}</span></div>` : `<div class="by one ${cls}"><span>${esc(v[0])}</span></div>`;

  let PICKS = [];
  // today's verse: deterministic per UAE calendar day, cycling through the sourced wisdom picks
  const picks = () => { PICKS = []; P.forEach((p, pi) => p.poems.forEach((q, qi) => (q.wisdom || []).forEach(w => PICKS.push({ pi, qi, w })))); };
  const dayNo = () => Math.floor((Date.now() + 4 * 3600e3) / 864e5);
  window.verseOfDay = () => { if (!PICKS.length) return null; const k = PICKS[(dayNo() * 7) % PICKS.length]; const p = P[k.pi], q = p.poems[k.qi]; return { p, q, k, lines: k.w.slice(0, 1).map(i => q.verses[i]) }; };

  // layers
  document.body.insertAdjacentHTML('beforeend', `
<section id="poetry" class="layer panel poe">
  <h1>الشعر <small>نصوص منقولة حرفياً من مصادرها</small></h1>
  <div id="poeWrap"><div id="poeList"></div><div id="poeSide"></div></div>
</section>
<section id="poem" class="layer panel poe">
  <div id="pmHead"><b id="pmTitle"></b><span id="pmPoet"></span><i class="orn"></i></div>
  <div id="pmBody"></div>
  <div id="pmSrc"></div>
</section>`);
  const empty = '<div class="poeEmpty">لا تتوفر قصائد حالياً</div>';
  const list = () => { $('#poeList').innerHTML = P.length ? P.map((p, i) => `<button class="pt" data-f data-poet="${i}" tabindex="-1"><b>${esc(p.short)}</b><small>${esc(p.name)} · ${p.poems.length} قصيدة</small></button>`).join('') : empty; };
  $('#poeList').innerHTML = '<div class="poeEmpty">جارٍ التحميل…</div>';

  const count = n => n === 1 ? 'بيت واحد' : n === 2 ? 'بيتان' : n <= 10 ? `${n} أبيات` : `${n} بيتاً`;
  let side = -1; const PAGE = 6;
  const card = (i, q, qi) => `<button class="pq" data-f data-poem="${i}:${qi}" tabindex="-1"><b>${esc(q.title)}</b>${bayt(q.verses[0])}<small>${count(q.verses.length)}</small></button>`;
  const more = (i, from) => { const n = P[i].poems.length - from; return n > 0 ? `<button class="pq pmore" data-f data-pmore="${i}:${from}" tabindex="-1"><b>المزيد</b><small>${n} قصيدة أخرى</small></button>` : ''; };
  // poem cards are paginated: PAGE at a time, «المزيد» appends the next page (keeps the DOM small)
  function showPoet(i) {
    if (i === side) return; side = i; const p = P[i];
    $('#poeSide').innerHTML = `<div class="pbio"><h2>${esc(p.name)}</h2>${p.bio ? `<p>${esc(p.bio)}</p><div class="psrc">${srcLine(p.bioSource, p.bioUrl)}</div>` : ''}</div>
      <div class="pqs">${p.poems.slice(0, PAGE).map((q, qi) => card(i, q, qi)).join('')}${more(i, PAGE)}</div>`;
  }
  function morePoems(el) {
    const [i, from] = el.dataset.pmore.split(':').map(Number); const box = el.parentNode;
    el.insertAdjacentHTML('beforebegin', P[i].poems.slice(from, from + PAGE).map((q, k) => card(i, q, from + k)).join('') + more(i, from + PAGE));
    el.remove(); setFocus(box.querySelector(`[data-poem="${i}:${from}"]`));
  }
  function openPoem(pi, qi, hi) {
    ret = cur && (cur.dataset.poem ? cur.dataset.poem : cur.id ? '#' + cur.id : null); const p = P[pi], q = p.poems[qi]; const hl = new Set(hi || []);
    $('#pmTitle').textContent = q.title; $('#pmPoet').textContent = p.name;
    $('#pmBody').innerHTML = q.verses.map((v, i) => bayt(v, (hl.has(i) ? 'hl ' : '') + 'pv" data-f data-v="' + i)).join('');
    $('#pmSrc').innerHTML = srcLine(q.source, q.url) + (q.note ? ` · ${esc(q.note)}` : '');
    $('#pmBody').scrollTop = 0; open('poem');
    if (hi && hi.length) setTimeout(() => setFocus($(`#pmBody [data-v="${hi[0]}"]`)), 60);
  }
  window.openPoem = openPoem;
  // Back from a poem returns focus to the card that opened it (poem card or «بيت اليوم»)
  let ret = null; const _back = back;
  window.back = back = function () { const top = stack[stack.length - 1]; _back(); if (top === 'poem' && ret) { const r = ret; setTimeout(() => { if (r[0] !== '#') showPoet(+r.split(':')[0]); const el = r[0] === '#' ? $(r) : $(`[data-poem="${r}"]`); if (el && el.offsetParent) setFocus(el); }, 60); } };

  // focus a poet → preview its poems; OK on a poet → jump into its poems
  const _sf = setFocus;
  window.setFocus = setFocus = function (el) {
    _sf(el);
    if (el && el.dataset && el.dataset.poet != null) showPoet(+el.dataset.poet);
    if (el && el.classList && el.classList.contains('pv')) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  };
  const _act = activate;
  window.activate = activate = function (el) {
    if (el && el.dataset.poet != null) { showPoet(+el.dataset.poet); return setFocus($('#poeSide .pq')); }
    if (el && el.dataset.pmore) return morePoems(el);
    if (el && el.dataset.poem) { const [a, b] = el.dataset.poem.split(':').map(Number); return openPoem(a, b); }
    if (el && el.dataset.vod != null) { const t = verseOfDay(); return t && openPoem(t.k.pi, t.k.qi, t.k.w); }
    if (el && el.dataset.v != null) return;
    return _act(el);
  };
  // returning to the list keeps the poet that was open
  const _open = open;
  window.open = open = function (id) { _open(id); if (id === 'poetry' && !P.length) loadPoetry(init); if (id === 'poetry' && P.length) { if (side < 0) showPoet(0); setTimeout(() => setFocus($(`#poeList [data-poet="${side}"]`)), 40); } };

  // ArrowRight off the poem cards goes back to the poet being shown, not the nearest one
  document.addEventListener('keydown', e => {
    if (e.key !== 'ArrowRight' || !cur || !cur.classList.contains('pq')) return;
    const r = cur.getBoundingClientRect(); if ($$('#poeSide .pq').some(x => x.getBoundingClientRect().left > r.right - 4)) return;
    e.stopImmediatePropagation(); e.preventDefault(); setFocus($(`#poeList [data-poet="${side}"]`));
  }, true);
  // home card «بيت اليوم» and the ambient line — built once the data is in (after first paint)
  let built = false;
  function init(d) {
    if (built || !d) { if (!d) $('#poeList').innerHTML = empty; return; } built = true;
    D = d; P = D.poets; picks(); list();
    if (stack[stack.length - 1] === 'poetry') { showPoet(0); setFocus($('#poeList [data-poet="0"]')); }
    const t = verseOfDay(); if (!t) return;
    if (IS_DREAM) { const el = document.createElement('div'); el.id = 'amVerse'; el.innerHTML = t.lines.map(v => bayt(v)).join('') + `<em>${esc(t.p.name)}</em>`; $('#ambient').appendChild(el); return; }
    const row = document.createElement('section'); row.className = 'row'; row.dataset.row = 'verse'; row.id = 'rowVerse';
    row.innerHTML = `<h2>بيت اليوم</h2><button id="vodCard" data-f data-vod="1" tabindex="-1"><i class="orn"></i><div class="vl">${t.lines.map(v => bayt(v)).join('')}</div><div class="vm"><b>${esc(t.p.name)}</b><small>${srcLine(t.q.source)}</small></div></button>`;
    const fav = $('#home .row[data-row="fav"]'); if (fav) fav.parentNode.insertBefore(row, fav.nextSibling);
    const amb = document.createElement('div'); amb.id = 'amVerse'; amb.innerHTML = t.lines.map(v => bayt(v)).join('') + `<em>${esc(t.p.name)}</em>`; $('#ambient').appendChild(amb);
  }
  setTimeout(() => loadPoetry(init), IS_DREAM ? 0 : 1200);
})();
