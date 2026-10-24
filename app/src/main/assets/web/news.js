/* ════════ 1.3.1 News screen — news lives only here (not on Home) ════════
   Featured story + list + categories + urgent line. Images: only real ≥600px photos, upgraded from
   the feed's thumbnail URL to the publisher's larger rendition, lazy-loaded, crisp (object-fit: cover),
   never a blurred full-screen background. No good image → a designed placeholder. */
(function newsScreen() {
  if (IS_DREAM) return;
  const MINW = 600;
  const ICO = { 'رياضة': 'football', 'طقس': 'weather', 'اقتصاد': 'picks', 'حكومة': 'briefing', 'محلي': 'news', 'عالمي': 'news' };
  // publisher thumbnail → large rendition (verified 24 Oct 2026 against the live feeds)
  window.bigImg = u => {
    if (!u) return '';
    if (/emaratalyoum\.com/.test(u)) return u.replace(/derivatives\/[a-z]+_\d+\//, 'derivatives/rectangular_960/');
    if (/images\.skynewsarabia\.com/.test(u)) return u.replace(/\/\d+\/\d+\/([^/]+)$/, '/1000/563/$1');
    if (/thenationalnews\.com\/resizer/.test(u)) return u.replace(/([?&])width=\d+/, '$1width=1280').replace(/([?&])height=\d+/, '$1height=720');
    return u;
  };
  const bad = new Set(JSON.parse(localStorage['news.badimg'] || '[]'));
  const markBad = u => { bad.add(u); localStorage['news.badimg'] = JSON.stringify([...bad].slice(-300)); };
  const ph = n => `<div class="nph" style="--c:${n.c}"><i class="ico" style="--ico:url(icons/${ICO[n.cat] || 'news'}.svg)"></i><span>${esc(n.src)}</span></div>`;
  const pic = (n, cls) => { const u = bigImg(n.img); return `<div class="npic ${cls}">${ph(n)}${u && !bad.has(u) ? `<img src="${esc(u)}" alt="" loading="lazy" decoding="async" data-src="${esc(u)}">` : ''}</div>`; };
  // reject low-res or failing images after load (event delegation: one listener, not one per image)
  document.addEventListener('load', e => { const im = e.target; if (im.tagName === 'IMG' && im.dataset.src) { if (im.naturalWidth < MINW) { markBad(im.dataset.src); im.remove(); } else im.classList.add('ok'); } }, true);
  document.addEventListener('error', e => { const im = e.target; if (im.tagName === 'IMG' && im.dataset.src) { markBad(im.dataset.src); im.remove(); } }, true);

  $('#news').innerHTML = `<div id="nwHead"><h1>الأخبار</h1><small id="newsMeta"></small></div>
    <div id="nwBar"><div class="opts" id="newsCats"></div><div id="nwUrgent" class="hidden"><b>عاجل</b><span id="nwUrgTx"></span></div></div>
    <div id="nwBody"><div id="nwFeat"></div><div id="nwList"></div></div>`;
  let FEAT = -1;
  const list = () => NEWS.filter(n => NCAT === 'الكل' || n.cat === NCAT);
  function feat(i) {
    const n = NEWS[i]; if (!n) { $('#nwFeat').innerHTML = ''; return; } FEAT = i;
    $('#nwFeat').innerHTML = `<div class="nwf" data-f data-nfeat="${i}" tabindex="-1">${pic(n, 'big')}
      <div class="nwfT"><em>${esc(n.cat)}</em><b dir="auto">${esc(n.title)}</b>
      ${n.sum ? `<p>${AITAG} ${esc(n.sum)}</p>` : n.desc ? `<p dir="auto">${esc(n.desc.slice(0, 220))}</p>` : ''}
      <small>${esc(n.src)} · ${ago(n.t)} · <span dir="ltr">${esc(((n.link || '').match(/^https?:\/\/(?:www\.)?([^/]+)/) || [, ''])[1])}</span></small></div></div>`;
  }
  window.renderNewsPanel = function () {
    $('#newsMeta').innerHTML = NEWS.length ? `${arCount(NEWS.length, 'خبر واحد', 'خبران', 'أخبار', 'خبراً')} · يتحدّث كل 15 دقيقة` : '';
    $('#newsCats').innerHTML = ['الكل', ...NCATS].map(c => `<button data-f data-ncat="${c}" class="${c === NCAT ? 'sel' : ''}" tabindex="-1">${c}</button>`).join('');
    const urg = NEWS.filter(n => Date.now() - n.t < 2 * 3600e3).slice(0, 1)[0];
    $('#nwUrgent').classList.toggle('hidden', !urg); if (urg) $('#nwUrgTx').innerHTML = `${esc(urg.title)} <s>${esc(urg.src)} · ${fmtTime(urg.t, false)}</s>`;
    const L = list().slice(0, 30);
    if (!NEWS.length) { $('#nwFeat').innerHTML = ''; $('#nwList').innerHTML = `<div class="nwEmpty">${online() ? 'جارٍ جلب الأخبار…' : 'لا يوجد اتصال بالإنترنت — ستظهر الأخبار عند عودة الاتصال'}</div>`; return; }
    if (!L.length) { $('#nwFeat').innerHTML = ''; $('#nwList').innerHTML = '<div class="nwEmpty">لا أخبار في هذا التصنيف الآن</div>'; return; }
    const f = L.find(n => n.img && !bad.has(bigImg(n.img))) || L[0]; feat(NEWS.indexOf(f));
    $('#nwList').innerHTML = L.filter(n => n !== f).map(n => { const i = NEWS.indexOf(n); return `<div class="nwc" data-f data-nitem="${i}" tabindex="-1">${pic(n, 'sm')}<div><b dir="auto">${esc(n.title)}</b><small>${esc(n.src)} · ${ago(n.t)}</small></div></div>`; }).join('');
    $('#nwList').scrollTop = 0;
  };
  // home no longer shows news: keep the data fresh for this screen only
  window.renderNews = function () { if (stack[stack.length - 1] === 'news') renderNewsPanel(); };
  const _act = activate;
  window.activate = activate = function (el) {
    if (el && el.dataset.nitem) { feat(+el.dataset.nitem); return; } // OK on a story shows it in the featured pane
    if (el && el.dataset.nfeat) { const n = NEWS[+el.dataset.nfeat]; if (n && A.speak) A.speak(n.title + '. ' + (n.sum || '')); return; }
    return _act(el);
  };
  const _open = open;
  window.open = open = function (id) { _open(id); if (id === 'news') { renderNewsPanel(); const f = $('#nwFeat [data-f]') || $('#newsCats .sel'); if (f) setFocus(f); } };
})();
