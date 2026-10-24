/* 1.3.1 — TV fit + performance profile. Loaded first.
   The UI is laid out on a fixed 1920×1080 stage that is scaled to the real screen (any size or DPR),
   letterboxed on black, so nothing can overflow the TV edges. */
'use strict';
const LITE = (() => {
  try { const p = JSON.parse((window.Android && Android.prefs && Android.prefs()) || '{}').fx; if (p === 'full') return false; if (p === 'lite') return true; } catch { }
  try { if (window.Android && Android.lowEnd && Android.lowEnd()) return true; } catch { }
  return (navigator.hardwareConcurrency || 4) <= 4 || (navigator.deviceMemory || 4) <= 2;
})();
document.documentElement.classList.toggle('lite', LITE);
function fitStage() {
  const w = document.documentElement.clientWidth || innerWidth, h = document.documentElement.clientHeight || innerHeight;
  const s = Math.min(w / 1920, h / 1080), x = (w - 1920 * s) / 2, y = (h - 1080 * s) / 2;
  document.body.style.transform = `translate(${x}px, ${y}px) scale(${s})`;
}
fitStage(); addEventListener('resize', fitStage);
// watchdog heartbeat (MainActivity reloads the page if this stops for 25 s)
setInterval(() => { try { window.Android && Android.beat && Android.beat(); } catch { } }, 5000);
