/* الكود 1.2 — Top Shelf + motion. Transform/opacity only; no per-frame blur. */
'use strict';
(function shelf() {
  if (IS_DREAM) return;
  const s = document.createElement('section'); s.id = 'shelf';
  s.innerHTML = `<div id="shArt"><div class="sa on"></div><div class="sa"></div></div><div id="shScrim"></div>
  <div id="shBody"><div id="shKick"></div><h1 id="shTitle"></h1><p id="shWhy"></p>
  <div id="shMeta"><span id="shPray"></span><span id="shWx"></span></div>
  <div id="shCtl"><button id="shGo" data-f tabindex="-1">شاهد الآن</button><div id="shDots"></div></div></div>
  <div id="shClock"><b id="shTime"></b><small id="shDate"></small><em id="shGreet"></em></div>`;
  $('#tabs').after(s);
  let i = 0, ai = 0;
  window.renderShelf = () => {
    const P = (typeof PICKS !== 'undefined' && PICKS.length) ? PICKS : null; if (!P) return;
    i = (i + P.length) % P.length; const p = P[i];
    $('#shKick').innerHTML = (p.ai ? '<span class="aitag">✦ ماذا أشاهد الآن</span>' : '<span class="kick">مقترح الآن</span>') + ` <span class="ty">${{ ip: p.it && { live: 'بث مباشر', movie: 'فيلم', series: 'مسلسل', episode: 'حلقة' }[p.it.kind], app: 'تطبيق', news: 'خبر', prayer: 'الصلاة', match: 'مباراة' }[p.type] || ''}</span>`;
    const t = $('#shTitle'); t.classList.remove('in'); void t.offsetWidth; const ct = p.type === 'ip' ? cleanLocal(p.title) : { title: p.title }; t.textContent = ct.title || p.title; if (ct.year) p.why = ct.year + ' · ' + (p.why || ''); t.classList.add('in');
    $('#shWhy').textContent = p.why || ''; const w = $('#shWhy'); w.style.animation = 'none'; void w.offsetWidth; w.style.animation = '';
    $('#shGo').textContent = { app: 'افتح', news: 'اقرأ', prayer: 'الأوقات', match: 'التفاصيل' }[p.type] || 'شاهد الآن';
    $('#shDots').innerHTML = P.map((_, j) => `<i class="${j === i ? 'on' : ''}"></i>`).join('');
    const ls = $$('#shArt .sa'), nx = ls[ai ^ 1];
    nx.style.backgroundImage = p.img ? `url("${p.img}")` : ''; nx.classList.toggle('none', !p.img);
    nx.classList.remove('kb'); void nx.offsetWidth; nx.classList.add('on', 'kb'); ls[ai].classList.remove('on'); ai ^= 1;
    if (p.type === 'ip' && !p.img && typeof enrich === 'function') enrich(p.it).then(w => { if (w && w.img && PICKS[(i + PICKS.length) % PICKS.length] === p) { p.img = w.img; nx.style.backgroundImage = `url("${w.img}")`; nx.classList.remove('none'); } }).catch(() => { });
  };
  document.addEventListener('keydown', e => {
    if (cur && cur.dataset.act === 'assistant' && cur.closest('#tabs') && e.key === 'Enter') { e.preventDefault(); e.stopImmediatePropagation(); return openCmd(false); }
    if (cur && cur.id === 'shGo' && e.key === 'ArrowUp') { e.preventDefault(); e.stopImmediatePropagation(); return setFocus($('#tabs .tab:not(.hidden)')); }
    if (cur && cur.id === 'shGo' && (e.key === 'ArrowLeft' || e.key === 'ArrowRight') && PICKS.length > 1) { i += e.key === 'ArrowLeft' ? 1 : -1; renderShelf(); e.preventDefault(); e.stopImmediatePropagation(); }
    if (cur && cur.id === 'shGo' && e.key === 'Enter') { e.preventDefault(); e.stopImmediatePropagation(); press(cur); setTimeout(() => openPick(PICKS[(i + PICKS.length) % PICKS.length]), 160); }
  }, true);
  setInterval(() => { if (stack.length === 1 && (!cur || cur.id !== 'shGo') && PICKS.length > 1) { i++; renderShelf(); } }, 12000);
  // animated clock: each changed digit slides in
  let last = '';
  const clk = () => {
    const now = Date.now(), t = fmtTime(now, false);
    if (t !== last) { $('#shTime').innerHTML = [...t].map((ch, k) => `<span class="${ch !== last[k] ? 'dg in' : 'dg'}">${ch}</span>`).join(''); last = t; }
    $('#shDate').textContent = dateLines(now).greg;
    try { const np = nextPrayer(), ms = np.t - now, h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60, sec = Math.floor(ms / 1000) % 60;
      $('#shPray').innerHTML = `<i class="ico" style="--ico:url(icons/prayers.svg)"></i>${PNAMES[np.i]} ${fmtTime(np.t)} · <b class="cd">${h ? h + ':' : ''}${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}</b>`; } catch (e) { }
    $('#shWx').innerHTML = `<i class="ico" style="--ico:url(icons/weather.svg)"></i>${[$('#wxTemp').textContent, $('#wxDesc').textContent].filter(Boolean).map(esc).join(' · ')}`;
  };
  clk(); setInterval(clk, 1000);
})();

/* press bounce */
function press(el) { if (!el) return; el.classList.remove('press'); void el.offsetWidth; el.classList.add('press'); setTimeout(() => el.classList.remove('press'), 380); }
document.addEventListener('keydown', e => { if (e.key === 'Enter' && cur && !e.repeat) press(cur); });

/* focus-follow glow + hero parallax depth */
(function glow() {
  const g = document.createElement('div'); g.id = 'glow'; document.body.appendChild(g);
  const _sf = setFocus;
  window.setFocus = setFocus = function (el) {
    _sf(el); if (!el) return;
    const hr = el.closest('#hbRows .row'); if (hr) hr.parentNode.scrollTo({ top: hr.offsetTop - 8, behavior: 'smooth' });
    requestAnimationFrame(() => setTimeout(() => {
      const r = el.getBoundingClientRect(), tile = el.classList.contains('tv');
      g.style.transform = `translate3d(${r.left + r.width / 2 - 300}px, ${r.top + r.height / 2 - 200}px, 0) scale(${Math.max(.6, r.width / 380)})`;
      g.style.opacity = 0; // glow retired in the Liwa Dusk pass
      const px = (r.left + r.width / 2) / innerWidth - .5, py = Math.min(1, r.top / innerHeight);
      document.documentElement.style.setProperty('--px', px.toFixed(3)); document.documentElement.style.setProperty('--py', py.toFixed(3));
      const sh = document.getElementById('shelf'); document.body.classList.toggle('deep', stack.length === 1 && (r.top > innerHeight * .55 || (sh && sh.getBoundingClientRect().top < -40)));
    }, 60));
  };
})();

/* staggered row entrance + section crossfade + tile→detail shared-element zoom */
function stagger(root) { [...root.querySelectorAll('.row, #shelf, .set, .sura, .nitem, #ptTable > div, #wDays > div')].forEach((r, k) => { r.style.setProperty('--d', Math.min(k, 14) * 45 + 'ms'); r.classList.remove('enter'); void r.offsetWidth; r.classList.add('enter'); }); }
const _open = open;
window.open = open = function (id) {
  const from = cur && cur.getBoundingClientRect && cur.getBoundingClientRect();
  if (from && id !== 'ambient' && from.width) {
    const z = document.createElement('div'); z.className = 'zoomer';
    const src = cur.querySelector('.art') || cur; const cs = getComputedStyle(src);
    z.style.cssText = `left:${from.left}px;top:${from.top}px;width:${from.width}px;height:${from.height}px;background:${cs.backgroundImage !== 'none' ? cs.backgroundImage : '#1c1a17'};background-size:cover;border-radius:${cs.borderRadius}`;
    document.body.appendChild(z);
    requestAnimationFrame(() => { z.style.transform = `translate(${-from.left}px,${-from.top}px) scale(${innerWidth / from.width},${innerHeight / from.height})`; z.style.opacity = '0'; });
    setTimeout(() => z.remove(), 420);
  }
  _open(id); const L = document.getElementById(id); if (L) { L.classList.remove('xf'); void L.offsetWidth; L.classList.add('xf'); stagger(L); }
};
const _back = back;
window.back = back = function () { _back(); const L = layerEl(); L.classList.remove('xf'); void L.offsetWidth; L.classList.add('xf'); };
setTimeout(() => stagger($('#home')), 50);

/* skeleton shimmer for empty / loading rows */
function skel(sel, n = 5, cls = '') { const el = $(sel); if (el && !el.querySelector('.tv')) el.innerHTML = Array.from({ length: n }, () => `<div class="tv skel ${cls}"><div class="art"></div><span class="sk"></span></div>`).join(''); }
['#newsRow', '#sugRow', '#ipLive', '#ipMovies'].forEach(s => skel(s));

/* sand-shimmer particles: ~36 specks, one canvas, paused when hidden or in panels */
(function sand() {
  if (IS_DREAM || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const c = document.createElement('canvas'); c.id = 'sand'; c.width = 1920; c.height = 1080; document.body.insertBefore(c, $('#home'));
  const x = c.getContext('2d'), P = Array.from({ length: 18 }, () => ({ x: Math.random() * 1920, y: Math.random() * 1080, r: .6 + Math.random() * 1.8, v: .15 + Math.random() * .35, a: Math.random() * 6.28 }));
  const f = () => { requestAnimationFrame(f); if (document.hidden || stack.length > 1) return; x.clearRect(0, 0, 1920, 1080);
    for (const p of P) { p.x -= p.v; p.y += Math.sin(p.a += .01) * .25; if (p.x < -5) { p.x = 1925; p.y = Math.random() * 1080; } x.globalAlpha = .25 + .35 * Math.abs(Math.sin(p.a * 2)); x.fillStyle = '#f1d27a'; x.beginPath(); x.arc(p.x, p.y, p.r, 0, 6.28); x.fill(); } };
  f();
})();
