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

## 2026-10-18: features beyond the reader (talk during M4)

Isaac's answers. All are ideas, not requirements: each needs a SPEC ID and an ADR where noted before it is built. Hard rules stay: read-only files, no network, no accounts, no Play Services.

### Library
- Decision: the start screen stays "recents only". Simple, one tap opens.
- Parked: title search in the list, only if the list passes about 20 items. Sort by last read, title or progress is a possible small step before that.
- Folders and collections were offered and not chosen. Folder grants stay a v2 idea.

### Bookmarks and highlights (wanted)
- Stored in the app database, keyed by document URI and anchor (never a page number, ANC-01). The file is never touched.
- Bookmarks: already listed for v1.1. Highlights are a new idea: a range of two anchors plus a color.
- Open points: what happens when the file changes (anchors may drift, show as "moved"); export of bookmarks and highlights as a Markdown file the user saves, which would be the only write and must go through the system "create document" picker; how highlights draw on a spread without breaking pagination (overlay only).

### Shortcuts and widget (wanted)
- Launcher shortcut for the last document and a "continue reading" widget. No network, no accounts.
- Open points: widget content is title and progress only; opening goes through the stored anchor. A widget needs Glance or RemoteViews: new dependency, so an ADR first.

### Read aloud (wanted)
- System TextToSpeech, offline voices only, following the anchor and turning pages as it goes.
- Pairs with A11Y-01 ("read continuously"). Open points: pause on call or headset unplug, which blocks are read (skip code and tables, announce them), language per document (LAY language tag).

### Reading history (wanted, simple and local)
- Time read per day and per document, stored only on the device, off the network, deletable in one tap. Planned as v1.2.
- Open points: what counts as reading time (screen on, chrome hidden, idle cut-off), where it is shown (a screen under settings, not on the reading surface).

### Suggested order after v1.0
1. Bookmarks (v1.1), then highlights.
2. Shortcut and widget.
3. Reading history (v1.2).
4. Read aloud, after A11Y-01 is proven in M6.

### Still not discussed
- Anything else beyond the reader. Ask again at the start of M5.

## 2026-10-21: handoff after M5 (math adapter, commit f475d8e)

State: M5 code is complete and checked on the foldable (SM-F976B): images, folder grant, TOC, search, footnotes, math (ADR-0006 Accepted). Gate green: `./gradlew --offline :core:model:test :core:parse:test :core:layout:test ktlintCheck detekt lintDebug testDebugUnitTest :app:assembleDebug checkPolicy verifyRoborazziDebug --continue`. Use `--rerun-tasks` when the corpus changes. `checkPolicy` needs the network once per new dependency (POM licenses).

### Next, in order
1. PRF-06 size: release APK is 12.3 MB universal (9.7 MB before math), budget 8 MB per ABI. R8 and minify are not configured. Set them up, then measure per ABI and decide on the KaTeX font payload. Also unlocks M6 measurements.
2. M6 device checks: TalkBack on paged semantics nodes (math, notes, links), 200% font scale, real timings and warm-up, thread safety of the math measurer, release build with R8.
3. Wire `Document.language` to the hyphenation locale (TYP-05).
4. Front matter metadata card (still shown as a code block).

### Known gaps
- Math: the per-call time budget checks after the call returns and cannot interrupt a runaway formula (the validator is the defense, see ADR-0006). The paged ellipsis for an over-tall failure source has no test or screenshot. Inline math shows as TeX text, not rendered.
- Scroll surface: Share, table-cell selection, TalkBack for paged links, cold-cache code-highlight pixel test. Text shifts if an image grows inside the viewport.
- `findDocumentPath` for opaque providers untested on a real device; Downloads provider folder grant untested.
- Possible 1 px AtomicFit rounding shift on old atomic blocks.
- CI does not run `verifyRoborazziDebug`; `docs/TESTING.md` lacks the reference platform for baselines (propose an edit). Re-record baselines only in a separate explained commit.

### Spec wording still open (propose before editing SPEC)
- PAG-07: 60% floor for math. `|` in a table cell needs `\|`. Macros are rejected. Paged semantics for math and images.

### Isaac's pending decisions and checks
- Freeze tokens in DESIGN.md at the end of M4 (still marked pending).
- M4 and M5 owner checkpoints on the device.
- Structure talk "anything else beyond the reader": ask at the start of M6 (M5 is closed).
