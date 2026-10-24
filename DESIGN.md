# الكود — v2.0 native (Compose for TV)

v2.0 replaces the WebView UI with Jetpack Compose and `androidx.tv:tv-material` 1.0. The «غسق ليوا» identity is unchanged. These are the native rules:

- **Brand.** The logo only, with no wordmark and no tagline. Greetings are fixed. There is no «شاهد الآن» anywhere, which `tools/qa/brand-check.sh` enforces.
- **Grid.** Everything is in dp. Overscan-safe margins are 48 dp horizontal and 27 dp vertical. Layout is forced RTL and fontScale is fixed at 1, so 1080p and 720p render the same 960×540 dp layout.
- **Focus.**
  - Standard tv-material Card focus scale (1.1) with a 3 dp ring, gold in move mode. No custom bounce or spring.
  - A fixed-pivot `BringIntoViewSpec`: rows pin the focused card at the 48 dp edge and columns keep the row title visible.
  - `focusRestorer` on every row.
  - Screens open as full-screen dialogs, so Back returns to the exact opener.
- **Motion.** None except the focus scale and the dream's 2.5 s photo crossfade. No parallax, Ken Burns or infinite animations.
- **Home.**
  - A calm hero with no button: greeting, clock, both dates, next prayer, weather and photo credit.
  - Rows, in order: المفضلة (reorderable) → مختاراتي → Liwaiptv (only if configured) → بيت اليوم → كل التطبيقات.
  - Back returns focus to the top.
- **Images.** Coil, decoded at the slot size with memory and disk cache. No blur.
- **Palette and type.** As below: charcoal, sand and one gold accent. Noto Kufi Arabic for UI, Noto Naskh Arabic for reading, and Amiri Quran for the Qur'an only.

---

# الكود TV — Art direction: «غسق ليوا» (Liwa Dusk)

**The idea.** Picture the minute after sunset in the Liwa dunes. Photography carries the screen and typography speaks. The UI chrome stays quiet. This should read like a magazine spread you watch from the sofa, not a dashboard.

## Principles
1. **One hero moment per screen.** Each screen has one thing that is clearly biggest: the Top Shelf headline, the hub title, the next-prayer time, or the command line. Everything else is at least 50% smaller.
2. **Asymmetry.** Content anchors to the reading edge (right, RTL) with wide negative space on the left. Nothing is centred except the Qur'an text.
3. **Full-bleed photography.** Every hero is a real photograph or real artwork: Wikimedia Commons landscapes, the provider's backdrop, or a confident Wikipedia image. A brand wordmark is never used as a hero. Photos get a fixed film-grain overlay and one warm light leak. Both are static and pre-rendered, never animated.
4. **Chrome recedes.** Tabs are quiet text with fine line icons. Only the focused element gets a light surface. Unfocused cards carry no borders and no glow.

## Typography (two families)
| Role | Font | Size / weight |
|---|---|---|
| Display headline | Noto Kufi Arabic | 112 / 800, tracking −1, line 1.05 |
| Screen title | Noto Kufi Arabic | 56 / 700 |
| Reading / reasons / synopses | Noto Naskh Arabic | 28–32 / 400–500, line 1.6 |
| UI labels | Noto Kufi Arabic | 22 / 500 |
| Captions | Noto Kufi Arabic | 16 / 400 at 40% white |

## Palette (tight)
- Charcoal `#0F0E0D` (base) and `#1A1917` (raised)
- Dune sand `#E6D3B0` (secondary text on photos)
- Muted date-palm green `#5E6B4E`, for "live" and "now" states only
- One gold accent `#D4A84B`: focus underline, ✦ AI marks, the active countdown
- No other colours. No decorative gradients, except photo scrims, which are functional.

## Signature motif: sadu line
The motif is a hand-tuned 6px band of sadu-weave triangles in gold, drawn as SVG. It appears in exactly two places:
1. Under the focused item, as the focus underline.
2. As the divider above a screen's hero kicker.

It never appears as a background, a border, or a pattern fill.

## Grid
- 8dp spacing grid.
- 90px safe area left and right; 48px top.
- Rows: 5 tiles of 316px with 40px gutters.

## Motion
| Moment | Timing and behaviour |
|---|---|
| Focus | Spring, 200ms, scale 1.08. The sadu underline draws in from right to left. |
| Hero moment | Headline rises 24px over 600ms, then the reason fades in 120ms later. |
| Hero backdrop | Ken Burns over 22s. |
| Section change | 320ms crossfade. The tile you opened zooms into the screen (shared element). |
| Rows | Staggered entrance at 45ms per row on first show only. |

All motion uses transform and opacity only. The only blur is a static `backdrop-filter` on the command sheet.

## Don'ts
- Emoji as icons
- Equal-weight card grids
- Glows, neon or outer shadows on unfocused items
- Centred symmetry
- Gradients as decoration
- Logos as hero art

## الشعر (1.3.0)
- Poems are the one centred layout besides Qur'an text: صدر on the right and عجز on the left, both hugging a 96px gutter. Text is Naskh, titles Kufi, with a gold sadu ornament under the title.
- Liwa dusk (`bg/liwa.jpg`) sits under a heavy charcoal scrim. The poet list is names only, with no portraits. The source is shown at 15px in --txt3.
- «بيت اليوم» is the row after favourites, and the same verse is shown on the screensaver. It is picked deterministically per UAE day from hand-picked sourced wisdom bayts, never generated.

## 1.3.1 — Platform research (what the big TV launchers do, and what we took)
Rule for anything adopted: **no blur, no backdrop-filter, no masks, no new animation.** Only static gradients, transform/opacity, and layout. Every row below was read from the linked public page on 2026-10-24.

| # | Platform | Idea (as stated in the source) | Source | Adopted? | Why / how |
|---|---|---|---|---|---|
| 1 | Google TV / Android TV | Back on the start screen with top navigation: "User is taken back to the top of the page by scrolling quickly and activates the focus to the menu." No exit gating. | https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv | **Yes** | Back on home (focus in a row) now jumps to the top and focuses the first tab. Pure JS, no animation. Back from the tabs does nothing extra (no confirmation dialog). |
| 2 | Google TV / Android TV | Axis rule: "Categories can be traversed on the vertical axis, and items within each category can be browsed on the horizontal axis." Avoid nested hierarchies. | same | **Yes (kept)** | Home is already vertical rows × horizontal tiles. News uses the same idea: categories on one horizontal bar, stories in one vertical list. |
| 3 | Google TV (2025 redesign) | Top-left navigation consolidated; "For You" became "Home"; Library moved under the profile menu. | https://9to5google.com/2025/09/24/google-tv-homescreen-redesign-official-rolling-out-to-more-users/ | Partly | Our top bar stays as quiet text tabs (RTL, so top-right). We have no accounts, so no profile menu. |
| 4 | tvOS (Apple HIG) | "Because focusing an item often increases its scale, you need to supply assets for the larger, focused size to ensure they always look sharp, and … make sure the larger item doesn't crowd the [surroundings]." tvOS focus uses a parallax effect. | https://developer.apple.com/design/human-interface-guidelines/focus-and-selection | Scale **yes**, parallax **no** | Focus lift = `transform: scale` only. Tiles are composed at 640×360 so nothing is upscaled when it lifts. Parallax/tilt was rejected: it needs per-frame transforms and layered art (GPU load on cheap sticks). |
| 5 | tvOS Top Shelf | Top Shelf highlights "new, featured, or recommended content"; the fallback static image is flipped and **blurred** by the system. | https://developer.apple.com/design/human-interface-guidelines/top-shelf | Hero **yes**, blur **no** | Home hero is our Top Shelf. 1.3.1 adds a static top scrim (tabs and clock stay readable on bright posters), a stronger right scrim (RTL text), and a static fade into the page (no hard edge). No blur. |
| 6 | Fire TV | Keep UI out of the outer 5% of every edge; "the focused item and on-screen text … should be fully within the inner 90%". | https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html | **Yes** | 1920×1080 stage with 96px/54px safe padding. Verified with a 5% overscan crop (`home-1080-overscan5.png`, `news-1080-overscan5.png`). |
| 7 | Fire TV | 10-ft UI: "low information density", "minimize the amount of text", "place the most important content first". | same | **Yes** | News moved off home into its own screen. Home hero has no text blocks. The verse card shows one بيت plus poet and source only. |
| 8 | Fire TV (2026 UI) | "All your apps are available on the home screen, and you can quickly rearrange the tiles, so your favorites are front and center." The rebuild cites "up to 20–30% gains in speed". | https://www.aboutamazon.com/news/devices/new-fire-tv-upgrades-features-2026 | **Yes (kept)** | Favourites are the first row and can be reordered (already in the app). Speed came first in this release too (see QA.md perf numbers). |
| 9 | Roku | "One screen, one purpose." "Never make a user … wait on animations." "Is it snappy? … Transitions, animations, and load times must all be optimized." | https://developer.roku.com/dev/docs/key-design-principles | **Yes** | News is a separate screen. Key-repeat skips smooth scrolling. Lite mode turns off Ken Burns and long fades. Focus handler under 16ms. |
| 10 | Roku | 5% overscan margin = "90 pixel left/right side margin and a 60 pixel top/bottom margin" at 1920×1080. | same | Partly | We use 96px horizontal (above 90) and 54px vertical (= 5% of 1080, the Fire TV / Android figure). This is 6px under Roku's 60px; no Roku build exists. |
| 11 | Roku | "Be more visual and celebrate artwork and images … make them big." Keep density low. | same | **Yes** | The news featured story uses a 1040px image (only images ≥600px wide are used; otherwise a branded placeholder). |
| 12 | Samsung Tizen | "Control feedback must be instant and distinct." Focused vs selected must be clearly different. A loading animation when loading takes a while. | https://developer.samsung.com/smarttv/design/design-principles.html | **Yes** | Focus = scale plus a light surface. Enter on a news item shows it in the featured pane immediately. Loading indicators: not changed in this release (no new spinner, to keep the GPU idle). |
| 13 | Samsung Tizen | "Using a grid is the best way to minimize confusion"; avoid diagonal placements. | same | **Yes** | Rows are strict grids. The news list is one column beside one featured pane, with no diagonal jumps. |
| 14 | Samsung One UI Tizen (2025) | "A fresh, clean interface that matches … Samsung smartphones", plus household profiles with per-profile recommendations. | https://news.samsung.com/us/samsung-launches-2025-neo-qled-tvs-powered-by-samsung-vision-ai | No | Profiles need accounts and storage. Out of scope for a single-household launcher. |

**Rejected on purpose (GPU):** tvOS parallax/tilt, the blurred Top Shelf fallback, frosted-glass panels (backdrop-filter), animated video backgrounds.

Before/after screenshots: `shots/v1.3.1/before/` (built from the v1.3.0 commit c1ad734 in the same harness) vs `shots/v1.3.1/`.
