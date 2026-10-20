# Bifolio Design System

Status: v1 direction, tokens frozen at the end of M4
Spec IDs: TYP-01 to TYP-10, UI-01 to UI-16, MOT-01 to MOT-09, A11Y-02 to A11Y-06

## 1. Identity

**Name.** A bifolio is one sheet folded once into two leaves. That is the device, and that is the spread. The brand rests on this single image.

**Character.** Literary, calm, precise. A well-set book, not a note-taking app. Warm paper, ink-dark text, one quiet accent.

**Three rules**

1. The page is the product. Chrome is a guest that leaves quickly.
2. Motion explains geometry. Text never bounces.
3. One of everything: one accent, one icon family, one stroke weight, one reading font at a time.

**App icon concept (explore in M4).** A single folded sheet seen from above: two leaves meeting at a spine, drawn with one continuous stroke on a paper-colored field. Monochrome variant for themed icons. No letterforms in the icon.

## 2. Reading themes

Fixed palettes for the reading surface (TYP-08, TYP-09). Contrast ratios are computed against the theme background (WCAG 2.x relative luminance).

| Token | Paper | Sepia | Dusk | Night |
|---|---|---|---|---|
| `page` (background) | `#F7F3EA` | `#EBDDC3` | `#1D1C1A` | `#000000` |
| `ink` (body text) | `#1F1C17` (15.3:1) | `#33281B` (10.7:1) | `#DCD6CB` (11.8:1) | `#C8C2B7` (11.9:1) |
| `inkMuted` (secondary) | `#5E574B` (6.5:1) | `#62523C` (5.6:1) | `#A59E92` (6.4:1) | `#8E887E` (6.0:1) |
| `accent` | `#8A4B2A` (6.1:1) | `#76391A` (6.6:1) | `#D9A47C` (7.7:1) | `#CF9A72` (8.5:1) |
| `link` | `#7A3F22` (7.4:1) | `#6B3316` (7.4:1) | `#E2B38F` (9.0:1) | `#D8AA86` (10.0:1) |
| `codeSurface` | `#EDE7DA` | `#E0D0B2` | `#272522` | `#121110` |
| `ink` on `codeSurface` | 13.8:1 | 9.5:1 | 10.6:1 | 10.7:1 |

Derived tokens (computed in code from the above, documented here when fixed in M4): `rule` (hairlines, quote bars), `selection`, `searchHit`, `searchHitFocused`, callout tints for note, tip, important, warning, caution (each must keep `ink` at 7:1 or more on the tint), and syntax colors for the token types in ADR-0007 (each at 4.5:1 or more on `codeSurface`).

Syntax colors (`codeTokens`, ADR-0007), color only, never weight or slant, so highlighted code keeps the plain advance. Ratios against `codeSurface` are all 4.5:1 or more (ContrastTest).

| Token | Paper | Sepia | Dusk | Night |
|---|---|---|---|---|
| `keyword` | `#8A2F5C` | `#7A2650` | `#E58FB8` | `#E085B0` |
| `string` | `#2F6B2A` | `#2A5E24` | `#9BCB85` | `#93C47D` |
| `number` | `#1F5F8A` | `#1B5278` | `#86BDE8` | `#7FB6E2` |
| `comment` | `#6A6354` | `#5C4C36` | `#A09A8E` | `#9A9488` |
| `type` | `#6B4E00` | `#5E4300` | `#E0C06A` | `#D9B960` |
| `function` | `#6A3F96` | `#5E3585` | `#C3A6EE` | `#BC9FE8` |
| `operator` | `#7A3F22` | `#6B3316` | `#E2B38F` | `#D8AA86` |
| `punctuation` | `#5E574B` | `#5A4A34` | `#B5AEA2` | `#ADA79B` |

Night is true black for OLED; its ink is deliberately dimmed to reduce halation.

**Chrome colors.** Chrome surfaces derive from the active reading theme (a slightly elevated `page` tone with `ink` content), so opening the chrome never flashes a different palette. Dynamic color applies to the library and settings only when the user enables it (off by default).

## 3. Typography

| Role | Family | Size | Line height | Notes |
|---|---|---|---|---|
| Body | Literata (default) | 18sp (14 to 28) | 1.5 (1.35, 1.5, 1.65) | `opsz` axis follows size when available |
| H1 | Body family, semibold | 1.75 x body | 1.2 | 1.4 lines above, 0.5 below |
| H2 | semibold | 1.45 x | 1.25 | |
| H3 | semibold | 1.22 x | 1.3 | |
| H4 | bold | 1.08 x | 1.35 | |
| H5 | bold small caps | 1.0 x | 1.4 | letter spacing +0.04em |
| H6 | bold, `inkMuted` | 0.92 x | 1.4 | |
| Code | JetBrains Mono | 0.88 x body | 1.45 | |
| Footnote marker | Body, superscript | 0.7 x | | `accent` color |
| Chrome labels | Body family or system sans (decide in M4) | 14 to 16sp | | |

Paragraph separation: 0.6 lines of space, no first-line indent (Markdown readers expect block paragraphs). Lists: hanging indent of 1.4em, markers in `inkMuted`.

## 4. Spacing, shape, elevation

- Base unit 4dp. Common steps: 4, 8, 12, 16, 24, 32, 48.
- Page margins and gutters come from SPEC section 4 formulas, not from this scale.
- Corner radius: 12dp sheets and popovers, 8dp code blocks and callouts, 20dp chips. Expressive shape morphing is allowed on chrome buttons only.
- Elevation: chrome uses tonal elevation (surface tone), no drop shadows on the reading surface.

## 5. Layout of chrome

| Window | Table of contents | Search results | Appearance |
|---|---|---|---|
| Compact (< 600dp) | Bottom sheet | Bottom sheet | Bottom sheet |
| Medium (600 to 839dp) | Bottom sheet | Bottom sheet | Bottom sheet |
| Expanded and wider (>= 840dp) | Side pane over the outer page, never over the fold | Side pane | Floating sheet anchored to the button |
| Tabletop posture | Control deck in the bottom half | Control deck | Control deck |

On a spread, panes slide over the page on the side of the button that opened them and never cross the fold.

External link confirmation (NAV-08) follows the same rule as the notes sheet: bottom sheet below 840dp, floating sheet from 840dp. It shows the full URL, a warning line when the URL carries userinfo, and two actions: Open and Cancel. Overlays on the reading surface (TOC, search, notes, link confirmation) carry a thin border in the theme rule color so they stay legible on Night.

## 6. Motion tokens

| Token | Use | Specification |
|---|---|---|
| `motion.reading` | Page turns, reflow, scroll snapping | `MotionScheme.standard()` spatial spring, no overshoot |
| `motion.readingEffects` | Highlights, fades on the reading surface | `MotionScheme.standard()` effects spring |
| `motion.chrome` | Toolbars, sheets, chips, buttons | `MotionScheme.expressive()` spatial spring |
| `motion.chromeEffects` | Chrome color and opacity | `MotionScheme.expressive()` effects spring (no overshoot) |
| `motion.fade` | Fade page turn, reduced motion | 180ms, emphasized easing |
| `motion.continuity` | Fold and unfold anchor move (MOT-05) | about 250ms spatial, standard scheme |
| `motion.anchorGlow` | Highlight on the anchor line after a jump or geometry change | fade from `selection` at 60% to 0 over 600ms |
| `motion.parallax` | Outer page lead during slide turns | 8% of page width |

Chrome exits use the same spring with higher stiffness so leaving is faster than arriving. All motion collapses to `motion.fade` or instant under reduced motion.

## 7. Signature moments

1. **The unfold.** Reader on the cover screen, unfold: the single column flows into a spread, and the line being read glides to its new place and glows briefly. This is the Play listing video.
2. **The book posture.** Half-fold the device: the gutter widens slightly, the pages settle to each side of the hinge like a book opening.
3. **The return chip.** After a jump, a small chip offers the way back; it follows the reader through folds.

## 8. Icons (Lucide)

Outlined, 1.5dp stroke at 24dp, `ink` or `inkMuted` color. Initial set: `book-open`, `folder-open`, `file-text`, `list` (table of contents), `search`, `type` (appearance), `sun-moon` (theme), `columns-2`, `rows-3` (scroll mode), `undo-2` (return chip), `external-link`, `chevron-left`, `chevron-right`, `x`, `info` (about), `trash-2`. Callout set: `info` (note), `lightbulb` (tip), `message-square-warning` (important), `triangle-alert` (warning), `octagon-alert` (caution).

## 9. Accessibility checklist for every screen

- Contrast per section 2; verified in screenshot tests.
- Touch targets 48dp minimum.
- Usable at 200% font scale.
- Focus indicator visible in chrome for keyboard users.
- Reduced motion path exists and is tested.
