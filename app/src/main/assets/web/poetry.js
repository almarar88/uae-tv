/* ════════ الشعر — Emirati poetry (1.3.0) ════════
   Every verse comes from data/poetry.js (built by tools/poetry/build.py from fetched sources,
   verbatim). Nothing here is generated: no AI text, no verses typed by hand. */
(function poetry() {
  const D = window.POETRY_DATA; const P = (D && D.poets) || [];
  const host = u => { try { return new URL(u).hostname.replace(/^www\./, ''); } catch { return ''; } };
  const srcLine = (name, url) => name ? `المصدر: ${esc(name)}${url ? ` <span dir="ltr">${esc(host(url))}</span>` : ''}` : '';
  const bayt = (v, cls = '') => v.length === 2 ? `<div class="by ${cls}"><span>${esc(v[0])}</span><span>${esc(v[1])}</span></div>` : `<div class="by one ${cls}"><span>${esc(v[0])}</span></div>`;

  // today's verse: deterministic per UAE calendar day, cycling through the sourced wisdom picks
  const PICKS = []; P.forEach((p, pi) => p.poems.forEach((q, qi) => (q.wisdom || []).forEach(w => PICKS.push({ pi, qi, w }))));
  const dayNo = () => Math.floor((Date.now() + 4 * 3600e3) / 864e5);
  window.verseOfDay = () => { if (!PICKS.length) return null; const k = PICKS[(dayNo() * 7) % PICKS.length]; const p = P[k.pi], q = p.poems[k.qi]; return { p, q, k, lines: k.w.map(i => q.verses[i]) }; };

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
  $('#poeList').innerHTML = P.length ? P.map((p, i) => `<button class="pt" data-f data-poet="${i}" tabindex="-1"><b>${esc(p.short)}</b><small>${esc(p.name)}</small></button>`).join('') : empty;

  const count = n => n === 1 ? 'بيت واحد' : n === 2 ? 'بيتان' : n <= 10 ? `${n} أبيات` : `${n} بيتاً`;
  let side = -1;
  function showPoet(i) {
    if (i === side) return; side = i; const p = P[i];
    $('#poeSide').innerHTML = `<div class="pbio"><h2>${esc(p.name)}</h2>${p.bio ? `<p>${esc(p.bio)}</p><div class="psrc">${srcLine(p.bioSource, p.bioUrl)}</div>` : ''}</div>
      <div class="pqs">${p.poems.map((q, qi) => `<button class="pq" data-f data-poem="${i}:${qi}" tabindex="-1"><b>${esc(q.title)}</b>${bayt(q.verses[0])}<small>${count(q.verses.length)}</small></button>`).join('')}</div>`;
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
    if (el && el.dataset.poem) { const [a, b] = el.dataset.poem.split(':').map(Number); return openPoem(a, b); }
    if (el && el.dataset.vod != null) { const t = verseOfDay(); return t && openPoem(t.k.pi, t.k.qi, t.k.w); }
    if (el && el.dataset.v != null) return;
    return _act(el);
  };
  // returning to the list keeps the poet that was open
  const _open = open;
  window.open = open = function (id) { _open(id); if (id === 'poetry' && P.length) { if (side < 0) showPoet(0); setTimeout(() => setFocus($(`#poeList [data-poet="${side}"]`)), 40); } };

  // ArrowRight off the poem cards goes back to the poet being shown, not the nearest one
  document.addEventListener('keydown', e => {
    if (e.key !== 'ArrowRight' || !cur || !cur.classList.contains('pq')) return;
    const r = cur.getBoundingClientRect(); if ($$('#poeSide .pq').some(x => x.getBoundingClientRect().left > r.right - 4)) return;
    e.stopImmediatePropagation(); e.preventDefault(); setFocus($(`#poeList [data-poet="${side}"]`));
  }, true);
  // home card «بيت اليوم» and the ambient line
  const t = verseOfDay();
  if (IS_DREAM) { if (t) { const el = document.createElement('div'); el.id = 'amVerse'; el.innerHTML = t.lines.map(v => bayt(v)).join('') + `<em>${esc(t.p.name)}</em>`; $('#ambient').appendChild(el); } return; }
  if (t) {
    const row = document.createElement('section'); row.className = 'row'; row.dataset.row = 'verse'; row.id = 'rowVerse';
    row.innerHTML = `<h2>بيت اليوم</h2><button id="vodCard" data-f data-vod="1" tabindex="-1"><i class="orn"></i><div class="vl">${t.lines.map(v => bayt(v)).join('')}</div><div class="vm"><b>${esc(t.p.name)}</b><span>${esc(t.q.title)}</span><small>${srcLine(t.q.source)}</small></div></button>`;
    const fav = $('#home .row[data-row="fav"]'); fav.parentNode.insertBefore(row, fav.nextSibling);
    const amb = document.createElement('div'); amb.id = 'amVerse'; amb.innerHTML = t.lines.map(v => bayt(v)).join('') + `<em>${esc(t.p.name)}</em>`; $('#ambient').appendChild(amb);
  }
})();
