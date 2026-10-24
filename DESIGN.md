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
