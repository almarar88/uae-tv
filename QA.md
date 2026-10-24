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
