# Backlog ideas

Ideas from product conversations with Isaac. Not requirements. Anything promoted to a requirement goes into `SPEC.md` with an ID first.

## 2026-10-17: structure talk after M2

Principle: the experience is simple and easy. One tap opens a document in full screen, defaults are already right (font size, theme, page layout), and configuration is optional, never a step before reading.

### Opening and reopening
- The start screen is the recents list. Isaac chose "pick from the list" over an automatic "continue reading".
- Tapping an item opens the document at its anchor, with no question asked.
- Opening must stay simple and fast. No tutorial, pop-up or permission request before the first page.
- Recents items show title, file name, last read time and progress (already UI-01), plus a short text preview of the start of the document.

### Open points
- Text preview is not in UI-01. Needs a spec edit and a decision on cost (parse only the first block, no layout) before it is built.
- Unavailable documents (moved, deleted, permission lost) should show as unavailable, not fail silently. Check what UI-01 and `core:data` do today. Proposed, not confirmed by Isaac.

### Already covered by the SPEC
- Full screen reading, center tap chrome (UI-03, UI-04).
- Progress as hairline, page or percent (UI-03, UI-05, UI-06).
- Recents, remove by swipe or menu (UI-01).
- Container transform from recent item to reader (MOT-04).

### Found on the foldable (SM-F976B, 2026-10-17, M3 slice 2 build)
- Center tap toggled scroll and paged mode. That was an M2 test shortcut, not a decision. Target: center tap shows and hides the chrome (UI-03, UI-04); mode switch lives in the appearance sheet (UI-10, M4), with a temporary control until then.
- Half-opened (book): text landed only in the left half, probably the per-side `34 x charW` floor of LAY-10 falling back to one column. Confirmed by arithmetic (sides 33.7 x charW against a 34 floor, assuming charW 0.5 em). Fixed: fold sides use a 28 x charW floor (LAY-10). Real `charW` on the device still to confirm.
- Scroll mode ignores the fold, so text crosses the hinge in book posture (LAY-14). Isaac chose to defer: revisit after paged mode is solid (options were two strips, one column on the wider side).
- First layout shows without the fold and relayouts about 120 ms later (WindowInfoTracker emits asynchronously). Candidate: hold the first pagination until the first emission, with a short timeout. Needs a spec line.

### Screen use (SM-F976B, 2026-10-17, after chrome tap build)
- Thick bands at top and bottom came from reserving system bar height while the bars were hidden. Fixed: pages lay out against the display cutout only, chrome and bars overlay. Isaac: "ficou ótimo".
- Vertical margin stays at `min(outerMargin, H/8)` (45 dp here). Proposal parked, not decided: `min(outerMargin, max(16dp, 1.5 x charW))`, about 2 more lines per page. Revisit with the M4 margin setting (LAY-05 scales 0.75, 1.0, 1.35).
- Parked: distribute the last-line remainder (about 13 dp on average) half top, half bottom.

### Not discussed yet
- Features beyond the reader (Isaac's wording). Ask again before M3 starts if more ideas come up.
