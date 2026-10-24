# QA log — الكود 2.0.0 (native Compose for TV)

**Harness.** The Android TV emulator (API 33 `android-tv` x86) was driven only by `adb shell input keyevent`. Scripts are in `tools/qa/` (`tv.sh`, `tabs.sh`, `flows.sh`, `flows2.sh`, `r720.sh`, `perf.sh`).

KVM was enabled on the box (`/dev/kvm` set to 0666), but the guest kernel never started under nested KVM. The emulator therefore ran under **TCG (`-accel off`)**, which is roughly 10× slower than real hardware. All functional checks below are real. **All timing and jank numbers are non-representative** of a real TV.

Screens were checked at 1920×1080 (density 320) and 1280×720 (density 213); both give a 960×540 dp layout. Screenshots are in `shots/v2.0/`. `tools/qa/brand-check.sh` returns PASS: no wordmark, no tagline and no «شاهد الآن» in `res/`, the manifest or Kotlin strings.

## Found → fixed during emulator QA
| # | Issue | Fix |
|---|---|---|
| 1 | The launcher opened on the **Settings** tab. Default initial focus landed on a random tab, and tabs switch on focus. | Focus is placed explicitly on «الرئيسية». Focus-driven tab switching is ignored until that is done. |
| 2 | Crash on the first D-pad move: `IllegalStateException: Release should only be called once` (lazy-list pinning with `focusRestorer`, a foundation 1.7.5 bug). | compose-bom raised from 2024.10.01 to 2025.01.01. |
| 3 | The hero's next-prayer and weather line was clipped by its fixed height. | The hero now wraps its content, and the clock has a trimmed line height. |
| 4 | Vertical pivot hid the row title above the focused row. | The pivot lead is now 44 dp, so the title stays visible. |
| 5 | Back on Home did nothing: focus deep in «كل التطبيقات» had no way back to the top. | Back on Home scrolls to the top and returns focus to the «الرئيسية» tab. Back on any other tab goes to Home. |
| 6 | Settings: the value text was unreadable on the focused row (sand on sand). | ListItem colours now set focused content to charcoal on sand. |
| 7 | Dialog panels sat off-centre, and the IPTV form's buttons ran off-screen. | Panels are centred on a scrim inside the safe area, with a 486 dp height cap and scrolling content. |
| 8 | Update check on 2.0.0 said «لديك أحدث إصدار (1.3.1)». | It now shows the installed version. |
| 9 | A news source that fails (Sky News returns 403 from some networks) could leave empty categories, and Credits listed a missing source. | Categories with no items are hidden. Credits lists only the sources that actually returned items. The 403 is swallowed per feed. |
| 10 | The news detail page printed the full article URL. | It now shows the source name only. The «فتح الخبر في المتصفح» button appears only when a browser resolves. |

## Verified on the emulator (keyevents)
- **RTL focus.** «الرئيسية» is the right-most tab. DPAD_LEFT goes to the next tab and DPAD_RIGHT on Home does nothing. Rows start at the right edge, and LEFT moves to the next card.
- **Pivot.** Horizontal rows keep the focused card at the 48 dp safe edge. Vertical lists keep the focused row's title visible. No overshoot or bounce, because the scale is the standard tv-material Card scale.
- **Focus restore.**
  - News list → detail → Back returns to the same story.
  - Qur'an grid → سورة البقرة → آية ٢ → tafsir → Back → Back returns to verse 2, then to the البقرة tile.
  - Back on Home returns to the top.
- **Favourites.**
  - Long-press opens the app menu: فتح / إزالة من المفضلة / تحريك داخل المفضلة / معلومات التطبيق.
  - Move mode highlights the card in gold and the title changes to «حرّك بالأسهم ثم اضغط موافق». LEFT moves the card one place later (RTL) and OK saves the order.
  - Verified order: Play Games ↔ Play Store swapped.
- **Tabs (all 9 rendered with live data).**
  - الأخبار: 3 feeds including Sky News on the emulator network, plus the «عاجل» strip.
  - الصلاة: Abu Dhabi times and countdown.
  - القرآن: 114 suras, the reader in Amiri Quran, and tafsir from Muyassar + Jalalayn, both credited.
  - الشعر: poet list, poem grid and poem view.
  - الطقس: Open-Meteo, 7 days.
  - الدوري: ADNOC Pro League upcoming fixtures, results and table.
  - اسأل: chips.
  - الإعدادات: all rows, the city chooser and the IPTV form.
- **Arabic shaping.** Kufi/Naskh joining is correct. Qur'an text shows Uthmani marks and end-of-āyah numerals. Mixed Arabic/English headlines align by their own direction.
- **Dream.** Set as the system screensaver via Somnambulator: credited photo, clock, both dates, next prayer and the verse of the day (`1080-dream.png`).
- **720p.** Identical dp layout, with nothing clipped and no overscan intrusion (`720-*.png`).

## Every tile and button → action
| Where | Element | Action (verified ✓ / code-reviewed •) |
|---|---|---|
| Top bar | 9 tabs | Focus switches tab ✓ |
| Home | Hero | Not focusable, by design (no button) ✓ |
| Home | المفضلة card | OK launches the app ✓ (Play Store opened). Long-press opens the menu ✓ |
| Home | Empty-favourites hint card | Toast explaining how to add favourites • |
| App menu | فتح / إضافة·إزالة المفضلة / تحريك / معلومات التطبيق | Launch ✓ / toggle + toast ✓ / move mode ✓ / system App Info • |
| Home | مختاراتي: موجز اليوم | Opens اسأل and requests the daily brief (needs an AI key) • |
| Home | مختاراتي: prayer within 25 min / today's match / usage apps | Opens الصلاة / الدوري / launches the app • |
| Home | Liwaiptv row (only when configured) | Opens the IPTV hub; channels play via the Liwaiptv/system player • (no test credentials) |
| Home | بيت اليوم card | Opens the poem view ✓ |
| Home | كل التطبيقات tiles | Launch ✓; long-press opens the menu • |
| News | Category pills | Filter ✓ (empty categories hidden) |
| News | Story row → detail | ✓; «فتح الخبر في المتصفح» only if a browser exists; «رجوع» ✓ |
| News | «إعادة المحاولة» (shown on failure) | Reloads • |
| Prayer | المدينة / تنبيه الأذان / تجربة التنبيه | Chooser • / toggle ✓ / posts a test notification • |
| Qur'an | Sura tile → reader → āyah → tafsir | ✓ ✓ ✓; «✦ اشرح بلغة مبسطة» uses the tafsir text only (needs AI) • |
| Poetry | Poet → poem tile → poem view | ✓ |
| Weather | المدينة / تحديث | Chooser • / reload • |
| League | Rows / تحديث | Read-only focusable rows ✓ / reload • |
| Ask | اكتب سؤالك / تحدّث / chips / مسح | Text panel • / voice recogniser (SEARCH key too) • / ask • / clear • |
| Settings | Each row | City chooser ✓, adhan toggle ✓, 12/24h •, background •, AI key / model •, IPTV form ✓, update check ✓, install update •, screensaver settings •, device settings •, credits • |

## Performance (non-representative: TCG emulator, no GPU acceleration)
See `shots/v2.0/gfxinfo-1080-home.txt` and the soak section below.

---

# QA log — الكود 1.3.1 (news screen, freeze fix, more poetry)

Harness: tools/preview (real live feeds: Sky News Arabia, Emarat Al Youm, The National; real Play icons) in headless Chrome. Screens were checked at 1920×1080, 1280×720 and 3840×2160, plus a 5% overscan crop. Screenshots are in `shots/v1.3.1/`. The "before" set in `shots/v1.3.1/before/` was rebuilt from commit c1ad734 (1.3.0) in the same harness. `tools/qa/brand-check.sh`: PASS.

## Freeze (top priority)
- **Root cause.** 1.3.0 sent every app's icon/banner as a multi-MB base64 PNG through `evaluateJavascript` on the UI thread, and repeated that on every package-change broadcast. The main thread stalled and the renderer was flooded with decode work. Separately, the GPU was overloaded by stacked full-screen layers: blend modes, backdrop-filter, the sand canvas, root parallax and Ken Burns.
- **Fix.**
  - Tiles are rendered once per app version to `cache/tiles/` and served as small `https://alcode.local/tile/…` URLs from `shouldInterceptRequest` (WebView IO thread).
  - Blur and backdrop-filter were removed. At most 2 background layers exist, and hidden layers release their images.
  - Lite mode on low-end devices.
  - Renderer priority IMPORTANT, `hardwareAccelerated=true`.
  - Watchdog: the page beats every 5s; MainActivity reloads the page after 25s of silence while visible.
  - `onRenderProcessGone` → `recreate()` instead of a dead white WebView.
- **Soak.** 30 minutes at 6× CPU throttle, with random D-pad keys, screens opened and closed, and `onAppsChanged` storms. GC'd heap, DOM nodes, listeners and key latency were sampled every 2 minutes.
  - Run 1 (code before the final polish): 8,067 keys. Heap 1.54 → 2.18 MB. DOM nodes 2,604 → ~3,400, flat from minute 2. Listeners flat at 94. Images flat at 112. Median key latency 46–55 ms, p90 120–171 ms. No crash, no hang.
  - Run 2 (release code, 22:51–23:24 Asia/Dubai): 5,706 keys over 32.6 min. Heap 1.78 → 2.06 MB (warm-up, min 4), then flat at 2.11–2.16 MB from min 6 to the end. DOM nodes 3,434–3,468. Listeners flat at 96. Images flat at 112. Median latency 47–56 ms; p90 111–175 ms (last sample 260 ms while the host was loaded). No crash, no hang.
- **Perf (1920×1080, favourites row, 24 keys, 4× throttle).** Key→paint median 91.9 → 31.1 ms, p90 226.9 → 55.4 ms, max 265 → 201 ms. `setFocus` 4.0 ms median, 5.8 ms max. At 6×: median 56.6 ms.

## Issues found → fixed (1.3.1)
| Screen | Issue | Fix |
|---|---|---|
| Home | News ticker and news row on home; faded text, a lone «اقرأ», a stray dot | News moved to its own screen. The home hero is photo-only. One pager dot is hidden when there is a single pick. |
| Home | Photo credit overlapped content | Credit is hidden on home; shown only on the screensaver and the credits screen |
| Home | 8px strip of the page background above the hero | Hero now starts at y=0 |
| Home | Tabs and clock unreadable on bright posters (e.g. Dune Part Two) | Static top scrim plus a stronger right scrim. No blur. |
| Home | Hard edge between the hero and the rows; 1px bright seam at 720p | Static gradient hand-off (`#shFade`) layered above the hero. The seam is gone at 720p (pixel-checked). |
| Home | Shelf art painted over the sticky tabs when scrolled; late `deep` state | Shelf isolated (z-index); tabs z 30 with a solid gradient; `deep` derived from focus synchronously |
| Home | Back did nothing on home | Back now jumps to the top and focuses the first tab (Google TV guidance) |
| Home | Stock/placeholder app banners (generic blue landscape) | Banner check: art shared by 2+ apps, or a flat blue frame with no text, falls back to the official-icon tile. Verified: no false positives on the 15 mock tiles or on synthetic blue wordmarks («Prime», «Disney+», «Shahid VIP»). |
| News | Blurry or tiny images, blurred backgrounds | URLs are upgraded to ≥960px renditions. Images under 600px or failing to load are dropped and remembered (LRU of 300). A branded category placeholder is used otherwise. No blurred backgrounds. |
| News | `scrollIntoView` shifted the whole layer and exposed a strip | Lists scroll their own box |
| Viewport | Headless check window was 87px short (false letterbox in our own shots) | Harness fixed. 1080p capture is a true 1920×1080. |
| Poetry | Verse card showed a title and the liwa background | One بيت plus poet plus source only, on a flat gradient |
| Poetry | Data loaded at boot | Lazy load 1.2s after boot. Remote `poetry.json` fetched at most once a day, validated, and cached, with the bundled copy as fallback. Poems are paginated 6 at a time («المزيد»). |

## Poetry counts (verbatim, scraped and cross-checked against the raw page; nothing written from memory)
9 poets, 106 poems, 1,505 verses: الكندي 12/173, فتاة العرب 12/244, زايد 10/71, محمد بن راشد 12/181, ابن ظاهر 12/171, راشد الخضر 12/173, العقيلي 12/132, الجمري 12/180 (new), ابن ياقوت 12/180 (new). Sources are 7awaya.ae and the DCT poetry archive. Partial pages are labelled as excerpts. Poems containing «شعار» were skipped so the brand gate stays clean.

## Needs real hardware
- Watchdog reload and `onRenderProcessGone` cannot be triggered in headless Chrome. The code paths are in MainActivity.
- Real overscan on Ahmed's TV.

# QA log — الكود 1.3.0 (الشعر)

Self-QA was done in the browser preview (tools/preview, headless Chrome over CDP at 1920×1080, and a 720p run at DPR ⅔). It covered the «الشعر» tab, the poet list, the poem view, the home «بيت اليوم» card, the ambient verse, D-pad focus and back paths, and RTL. Screenshots are in `shots/v1.3/`.

- [x] **Brand check:** `tools/qa/brand-check.sh` → **PASS** (after two fixes below).
- [x] versionCode 5, versionName 1.3.0, signed with the release key (SHA-256 883397f1…6f86a).
- [x] Every verse is verbatim from a fetched source. `tools/poetry/build.py` checks every hemistich against the fetched page text and fails the build otherwise. Only kashida, footnote numbers and a stray trailing dash are removed. No verse is AI-generated or typed by hand.
- [x] Each poem and bio shows its source in small text. Credits list the poetry sources.

| Screen / state | Found | Fixed | Notes |
|---|---|---|---|
| Brand check | 2 | 2 | «شعار» matched inside «أشعار» (a Zayed article title in a note, and the Majidi bio). Both reworded; verses untouched |
| Poem view | 1 | 1 | `#poem {display:flex}` overrode `.layer` hiding, so the poem sat over every panel (now `.active` only) |
| Poet list | 1 | 1 | 7th poet clipped at 1080p; tighter rows, last row inside the safe area at 1080p and 720p |
| Poem cards | 2 | 2 | first bayt wrapped mid-hemistich in the narrow card (now stacked); «2 أبيات» → «بيتان» |
| Focus / back | 3 | 3 | Back from a poem lost the card that opened it (poet list and home card); → from poem cards jumped to the nearest poet instead of the shown one |
| Data | 3 | 3 | Khidr parse picked up a footnote line; trailing «-» in one MBR hemistich; one wisdom index pointed to the wrong bayt |
| Empty state | 0 | 0 | No data → «لا تتوفر قصائد حالياً», no home card, no ambient verse (code path reviewed) |

# QA log — الكود 1.2.1 (previous)

Self-QA was done in the browser preview (tools/preview, Chrome at 1920×1080, plus a 720p run at DPR ⅔, a 4K run at DPR 2, and an offline run). It was repeated until a full pass had no open issues.

## Checklist (run before every release)
- [x] **Brand check:** run `tools/qa/brand-check.sh`. It greps every string in `res/` and `assets/web` for wordmarks and taglines (`الكود TV`, `Alcode TV`, `Majlis TV`, `تلفزيونك`, `الإماراتي الذكي`, `التلفزيون الذكي`, slogan/tagline…). The result must be PASS with zero matches. The logo appears alone everywhere: splash, About, screensaver, TV banner, settings header. **Result: PASS.**
- [x] App label is «الكود», versionCode 4, versionName 1.2.1, signed with the release key (SHA-256 883397f1…6f86a).
- [x] All AI output is labelled ✦. With no key, the app degrades to fixed text.
- [x] Prayer times match the Aladhan API (method 16) for Abu Dhabi and Dubai on 2026-10-24, e.g. Abu Dhabi 05:07 / 06:25 / 12:10 / 15:23 / 17:51 / 19:06.
- [x] No third-party logo files in git. Play icons are fetched on the device at runtime, or by the preview server only.

## Issues by screen (found → fixed)
| Screen / state | Found | Fixed | Notes |
|---|---|---|---|
| Splash / About / screensaver / banner / settings header | 3 | 3 | wordmark «الكود TV» and the tagline removed; logo only |
| Home header (clock, greeting) | 3 | 3 | clock unreadable on bright heroes (scrim and shadow), clock clipped, greeting now from the fixed set |
| Home hero / tab bar | 2 | 2 | tab logo overlapped row titles when scrolled; AI "why" text replaced with fixed reasons |
| App tiles (grid, favourites) | 6 | 6 | tvOS-style composition; Disney+/STARZ box-in-box; STARZ blur smear; wrong beIN listing in the preview; Settings now a graphite gear; Arabic initials took «ال» (now the whole short name) |
| Prayer / weather panels | 2 | 2 | city wrapped onto its own line; Arabic number agreement |
| News | 2 | 2 | raw percent-encoded URLs (now the domain only); RTL punctuation in English titles |
| Quran reader / tafsir | 1 | 1 | six sura-name spellings |
| Reminders / command bar | 2 | 2 | «الكود TV» in a reminder string; minute and hour agreement («الآن» for 0) |
| Offline | 1 | 1 | weather showed «°-- · الطقس»; now «الطقس غير متاح» |
| No AI key | 0 | 0 | command bar shows help text only |
| Kids lock, imsakiya, football, ambient, credits, briefing | 0 | 0 | briefing shows a loading state until the AI answers |
| 720p / 4K | 0 | 0 | the fixed 1920 viewport scales cleanly. The 4K overlap seen in round 1 was a capture artefact and was not reproduced |
| **Total** | **22** | **22** | **0 open** |

## Needs real hardware to verify
- Play-icon fetch, the 30-day cache, and ≥512px rejection on a real device and network.
- Adaptive and legacy icons and banners of apps installed on a real TV.
- Media pause and resume, adhan playback, and the screensaver (DreamService).
- D-pad focus, Back, long-press, and voice input on a real remote.
- Performance and memory on low-end TV SoCs (tile composition in batches of 4).
