# Bifolio Product Specification

Version: 1.0 (baseline for v1.0 development)
Status: Approved for implementation
Owner: Isaac D'Césares
Package: `dev.dcesares.bifolio`

This document is the single source of truth for what Bifolio does. Code implements this spec; when the two disagree, the spec wins until it is amended through a pull request that edits this file. Every requirement has a stable ID (for example `LAY-03`). Issues, commits and pull requests cite the IDs they implement or test.

Normative words: **MUST** is required for v1.0. **SHOULD** is expected unless an ADR records why not. **MAY** is optional.

---

## 0. Product definition

**One sentence.** Bifolio is a read-only Markdown reader for Android that typesets documents like a book, and that turns a foldable phone into a true two-page spread.

**Principles**

1. **The page is the product.** Chrome appears only on request. Nothing competes with the text.
2. **Foldable-native, phone-excellent.** The spread is the signature; the single page on a regular phone must be just as good.
3. **Read-only by design.** No editing, no saving, no writing to user files. Simplicity is a feature.
4. **Private by construction.** No network permission in v1. Nothing leaves the device.
5. **Fast and calm.** First page in under half a second; motion explains geometry and never decorates text.

**Target devices.** Android 8.0 (API 26) and newer. Primary design targets: book-style foldables (Galaxy Z Fold series including the 4:3 wide form, Pixel Fold series, Honor Magic V, Oppo Find N), tri-folds, regular phones, tablets, and desktop windowing (Samsung DeX, connected displays).

---

## 1. Scope

### 1.1 In scope for v1.0

| ID | Capability |
|---|---|
| SCO-01 | Open Markdown files from the system picker, from other apps (view and share intents) and from recents |
| SCO-02 | Render CommonMark plus the GFM extensions listed in section 3.3 |
| SCO-03 | Paged mode (default) and scroll mode, both driven by the same document model |
| SCO-04 | Automatic column and spread layout that understands folds and postures (section 4) |
| SCO-05 | Reading position that survives fold, unfold, rotation, resize, font changes and process death (section 6) |
| SCO-06 | Typography controls, four reading themes (section 8) |
| SCO-07 | Table of contents, in-document search, footnote popovers, internal links with return |
| SCO-08 | Local images resolved relative to the document, with a one-time folder grant |
| SCO-09 | Native math rendering and code syntax highlighting |
| SCO-10 | Reading progress and time remaining |
| SCO-11 | Accessibility: TalkBack, font scaling, reduce motion, keyboard navigation |

### 1.2 Explicitly out of scope for v1.0

| ID | Excluded | Reason |
|---|---|---|
| OUT-01 | Any form of editing or saving | Read-only by design |
| OUT-02 | PDF or any export | Not reading; large surface |
| OUT-03 | Remote images and any network access | Privacy promise (planned as opt-in for v1.1) |
| OUT-04 | Mermaid rendering | No maintained native renderer; show source with a diagram label (planned offline for v2) |
| OUT-05 | Cloud integrations (GitHub, Drive, Dropbox) | Scope |
| OUT-06 | Annotations, highlights, bookmarks beyond recents | Scope (bookmarks planned v1.x) |
| OUT-07 | Sync, accounts, analytics, crash reporting SDKs | Privacy and F-Droid compatibility |
| OUT-08 | Page-curl animation | Dated, costly across a hinge |
| OUT-09 | Raw HTML rendering | Security and fidelity; safe subset becomes text (DOC-09) |
| OUT-10 | Translated UI | English only in v1; all strings live in resources so translation is a data change |
| OUT-11 | WebView anywhere in the app | See ADR-0002 |

---

## 2. Glossary

| Term | Meaning |
|---|---|
| **Document** | One decoded Markdown file plus its URI and metadata |
| **Block** | One top-level renderable unit produced by the parser: heading, paragraph, list (with its items as sub-units), code block, table, image, display math, quote, callout, rule, front matter card, footnote definition |
| **Block index** | Zero-based position of a block in document order. Stable for a given document content |
| **Measure** | Line length of a text column, expressed in characters of the body font |
| **charW** | Average body-font character width: measured width of `abcdefghijklmnopqrstuvwxyz` divided by 26, at the current font and size |
| **Column** | One vertical text area. In paged mode a column is one page |
| **Page** | One column of laid-out content in paged mode |
| **Spread** | The set of pages visible at once (1, 2 or more columns) |
| **Slice** | The part of one block shown on one page: `(blockIndex, startLine, endLine)` for text blocks, or the whole block for atomic blocks |
| **Geometry** | Everything that affects layout: window size in px, density, insets, folding features, column count, margins, gutter, font settings, theme metrics |
| **Anchor** | Reading position independent of geometry: `(blockIndex, charOffset)`, where `charOffset` counts source characters from the start of the block |
| **Separating fold** | A `FoldingFeature` that intersects the window and has `isSeparating == true`, or whose `occlusionType == FULL` |

---

## 3. Document pipeline

### 3.1 Input

| ID | Requirement |
|---|---|
| DOC-01 | MUST handle `ACTION_VIEW` and `ACTION_SEND` for `text/markdown`, `text/x-markdown` and `text/plain`. MAY also accept `application/octet-stream`, but only opens it when the display name ends in `.md`, `.markdown`, `.mdown` or `.mkd`; anything else is closed immediately with a short "Not a Markdown file" message. MUST NOT declare intent filters for images, PDFs or other unrelated types |
| DOC-02 | MUST open files through the Storage Access Framework (`ACTION_OPEN_DOCUMENT`) and take a persistable read permission when the provider allows it |
| DOC-03 | MUST decode UTF-8 (with or without BOM). MUST detect UTF-16 by BOM. Other encodings: attempt Windows-1252 fallback and show a quiet notice that characters may be wrong |
| DOC-04 | MUST normalize line endings for parsing (CRLF and CR become LF) without other changes |
| DOC-05 | Files larger than 8 MiB (8,388,608 bytes) MUST open in scroll mode with a notice and no pagination; files larger than 32 MiB MUST be refused with a clear message |

### 3.2 Parsing

| ID | Requirement |
|---|---|
| DOC-06 | MUST parse with commonmark-java (ADR-0003) off the main thread, producing an immutable block model (ADR-0004) |
| DOC-07 | MUST keep source positions so every block maps back to a character range of the source |
| DOC-08 | MUST cap nesting depth (lists, quotes) at 32 levels; deeper content is flattened, not dropped |
| DOC-09 | Raw HTML blocks and inline HTML MUST render as plain text, except `<br>`, `<sup>`, `<sub>`, `<kbd>`, `<mark>`, `<u>` which map to styling, and `<details>/<summary>` which map to a collapsible block |

### 3.3 Supported syntax

| ID | Feature | Rendering |
|---|---|---|
| SYN-01 | Headings 1 to 6, ATX and Setext | Typographic scale (section 8); anchors generated GitHub-style, including non-Latin text |
| SYN-02 | Paragraphs, emphasis, strong, strikethrough, inline code, hard and soft breaks | Standard |
| SYN-03 | Ordered, unordered and nested lists; task lists | Task boxes are read-only glyphs, never toggles |
| SYN-04 | Block quotes | Continuous left rule, splits by line across pages |
| SYN-05 | Fenced and indented code | Monospace, highlighted (SYN-11), wrap or horizontal scroll per setting |
| SYN-06 | Tables (GFM) with alignment | See PAG-06 |
| SYN-07 | Links, autolinks, reference links | Internal anchors navigate in-document; external links open after confirmation (NAV-08) |
| SYN-08 | Images (relative paths inside a granted folder, or data URIs up to 2 MB of encoded text). Absolute paths, `file:`, `content:` URIs in document text and remote URLs are never loaded. Only a paragraph holding a single Markdown image becomes an image block; inline, multiple and linked images, and `<img>`, show alt text. Image pixels map 1:1 to dp | Atomic block; alt text shown when unavailable |
| SYN-09 | Footnotes | Superscript marker; tap opens a popover (UI-09). Definitions are blocks at their source position; the UI may also gather them into a notes list at document end |
| SYN-10 | GitHub alerts (`> [!NOTE]`, `TIP`, `IMPORTANT`, `WARNING`, `CAUTION`) | Callout block with Lucide icon and tinted rule |
| SYN-11 | Code highlighting for at least: kotlin, java, python, javascript, typescript, json, yaml, toml, bash/shell, sql, html, css, markdown, c, cpp, go, rust, swift, diff | Via the highlighter adapter (ADR-0007); unknown languages render unhighlighted. Aliases: `xml` and `svg` use the html rules, `jsonc` uses json |
| SYN-12 | Math: `$...$` inline, `$$...$$` and fenced `math` display | Native renderer (ADR-0006); on parse failure show the TeX source in monospace with a subtle error mark. `$...$` is inline math when the opening `$` is followed by a non-space on the same line, the closing `$` is not preceded by whitespace and not followed by a digit, so `$5 and $10` is text. `$$...$$` is display math when it is the only content of a paragraph, inline otherwise. `\$` is a literal dollar, an unclosed delimiter stays literal, and math never spans paragraphs. Fenced `math` blocks are display math. Inline formulas are laid out as a text run of their TeX source until the renderer supplies inline sizes. Inline formula source is searchable; display and fenced math are not |
| SYN-13 | YAML front matter | Metadata card at top; `title` overrides the first H1 as document title; `lang` (alias `language`, a BCP 47 tag) sets hyphenation locale |
| SYN-14 | Mermaid fences | Source shown as code with a "Diagram (not rendered)" label |
| SYN-15 | Horizontal rules | Centered ornament spacing, never a page break (it does not force one and is never split; it may sit at the top of a page) |

---

## 4. Layout geometry

### 4.1 Inputs

| ID | Requirement |
|---|---|
| LAY-01 | Layout MUST derive only from the **window** (WindowMetrics, insets) and the **folding features that intersect the window**. MUST NOT branch on device model, physical screen size or `isTablet`-style checks |
| LAY-02 | Fold data MUST come from Jetpack WindowManager `WindowInfoTracker.windowLayoutInfo`, collected lifecycle-aware (ADR-0010) |
| LAY-03 | Text MUST stay inside `WindowInsets.safeDrawing` and away from display cutouts. The reading surface draws edge to edge behind system bars |

### 4.2 Measure and column count

All values in px at the current density; `W` is the usable window width after insets.

| ID | Rule |
|---|---|
| LAY-04 | `minMeasure = 40 × charW`, `targetMeasure = 66 × charW`, `maxMeasure = 75 × charW` |
| LAY-05 | `outerMargin = max(20dp, 0.06 × W)` per side, rounded to whole px. User margin setting scales this by 0.75, 1.0 or 1.35 |
| LAY-06 | `baseGap = max(32dp, 2.5 × charW)` |
| LAY-07 | **Auto columns:** `cols = clamp(floor((W − 2·outerMargin + baseGap) / (minMeasure + baseGap)), 1, 2)` |
| LAY-08 | **Forced columns** (user chose 1 or 2) are honored whenever each column keeps at least `28 × charW`: `cols = min(userChoice, autoColsWithFloor)` where `autoColsWithFloor` uses a floor of `28 × charW` instead of `minMeasure`. This lets an unfolded foldable show 2 columns without any hinge, even where Auto picks fewer. A forced count that cannot fit drops columns, and the appearance sheet says how many the window fits. `maxMeasure` bounds only the single-column layout: with 2 columns each column takes the available width divided evenly, never below the floor. Page height is the text region height minus two vertical margins, each `min(outerMargin, H / 8)`, where `H` is the height of the text region: the window height, except in tabletop (POS-04) |
| LAY-09 | If `cols == 1` and the column would exceed `maxMeasure`, the column is centered and the extra width becomes margin |
| LAY-10 | If a **separating vertical fold** intersects the window, `cols` MUST be even (force 2 if 1 and each side fits `28 × charW`). Each side is measured after subtracting `outerMargin`, `innerMargin` and the gutter share on that side. The fold rule wins over a user-forced column count: a forced 1 becomes 2 in this case, because a single column would cross the hinge (LAY-14). In every fold case, two columns are used only if each side is at least `28 × charW` (the fold floor, the same as the floor of LAY-08, which here is measured after the gutter and the book-posture margin have taken their share of each side). If they cannot fit, fall back to 1 column on the wider side of the fold, of width `min(room of that side, maxMeasure)` (LAY-09). That column is centered in the **window**; when centering would put it inside the fold band (fold bounds plus `innerMargin`, as the gutter) it is shifted away from the fold by the smallest amount that clears the band, and never closer to the window edge than `outerMargin`. If neither side has at least 1 px, ignore the fold for column placement (the no-fold geometry applies; LAY-14 cannot hold). A fold touching the window edge with zero width counts as not intersecting |

### 4.3 Gutter and fold alignment

| ID | Rule |
|---|---|
| LAY-11 | Without a separating fold, columns have equal width and are separated by `baseGap` |
| LAY-12 | With a separating vertical fold, the gutter MUST be centered on the fold bounds: `gutter = max(baseGap, foldWidth + 2 × innerMargin)` where `innerMargin = max(16dp, 1.5 × charW)`. Columns on each side may differ in width so that the gutter tracks the hinge exactly. `foldWidth` is the width of the reported fold bounds whatever the `occlusionType`, so LAY-14 holds for every fold. Platforms usually report width 0 for `NONE`, in which case `gutter = max(baseGap, 2 × innerMargin)` and the gutter is still centered on the fold |
| LAY-13 | In book posture (`HALF_OPENED` + `VERTICAL`), add `12dp` per side to `innerMargin` to compensate for the tilted page edge. The gutter therefore grows by 24dp in total. Example: charW 9dp, innerMargin 16dp, fold width 0: gutter 32dp in flat posture and 56dp in book posture |
| LAY-14 | With a **separating** fold (`isSeparating == true`, which includes `OcclusionType.FULL`), no glyph, image, rule or highlight MAY ever be drawn inside the fold bounds plus `innerMargin`, even when `occlusionType == NONE`. With a non-separating fold (flat, or any fold reporting `isSeparating == false`) there is no constraint: text may cross the fold as in a flat open book, and the plain column math of LAY-07 to LAY-09 and LAY-11 applies |

### 4.4 Posture behavior

| ID | Posture | Detection | Layout |
|---|---|---|---|
| POS-01 | Phone / cover screen / folded | No intersecting fold, narrow window | Auto columns (normally 1) |
| POS-02 | Unfolded flat, no separating fold | No intersecting fold, or fold with `isSeparating == false` | Auto columns by LAY-07, with no fold constraint (LAY-14 does not apply to non-separating folds). 1 or 2 depending on window width and font size, or the forced count of LAY-08 |
| POS-03 | Book | `HALF_OPENED` + `VERTICAL`, or any separating vertical fold | Even columns, gutter on hinge (LAY-10 to LAY-13). `OcclusionType.FULL` implies separating. Columns and gutter are reshaped only when the fold is separating; a non-separating half-opened vertical fold is posture Book with the plain column math |
| POS-04 | Tabletop | `HALF_OPENED` + `HORIZONTAL` (separating or not) | Text occupies the **text region**: the part of the window from `y = 0` to the fold top edge, over the full window width. Columns are the plain LAY-04 to LAY-09 result for the full window width `W` (a horizontal fold adds no gutter and no fold column rules; LAY-10 to LAY-14 concern vertical folds), so 1 or 2 columns by LAY-07. Page height and the vertical margin use the region height as `H` (LAY-08), so no text is drawn below the fold top edge. If the region is shorter than 4 body lines (`4 × bodyPx × lineHeight`), or the deck would be shorter than `96dp` (2 × 48dp touch targets), the fold is ignored: posture is Standard, text uses the whole window and there is no deck. The **deck region** is the rest of the window, full width, from the fold bottom edge (the fold top edge when the hinge has zero height) to the window bottom. It shows the control deck: large previous and next zones, progress scrubber, table of contents button. Anchors (ANC-01 to ANC-07) come only from the text region; the deck holds controls only, never text. Both regions are exposed by the layout as `Geometry.textRegion` and `Geometry.deckRegion` in window px |
| POS-05 | Multi-window, pop-up, desktop window | Window-based only | Same rules; a fold matters only if it intersects the window. Must remain usable down to 320dp wide and 360dp tall |
| POS-06 | Tri-fold, tablet, large desktop window | Same rules | Auto columns stay at 2 (three columns were dropped as confusing) |

---

## 5. Pagination (paged mode)

| ID | Rule |
|---|---|
| PAG-01 | Pagination MUST run off the main thread on a `Geometry` snapshot; results are immutable `Page` lists made of slices |
| PAG-02 | Paragraph-like blocks split between lines. **No widows or orphans:** a split leaves at least 2 lines on each side; a block of 3 lines or fewer is not split |
| PAG-03 | **Keep with next:** a heading MUST be followed by at least 2 lines of its next block on the same page; otherwise the heading moves to the next page |
| PAG-04 | Code blocks split between lines (never inside a line). A continued code block shows a quiet continuation mark at the top of the next page |
| PAG-05 | Quotes and callouts split by line; the left rule is continuous on both pages; the callout title is not repeated |
| PAG-06 | Tables never split inside a row. A table taller than a page splits between rows and repeats the header row. A table wider than its column renders as a framed figure that scrolls horizontally inside the page and can open full screen on tap. A row taller than an empty column is placed alone on its page and may overflow it (clip or inner scroll is a UI decision). A split never leaves only the header on a page that already has content: the whole table moves to the next page. The continuation mark is drawn in the margin and takes no line height. Each column is at least as wide as its longest unbreakable word plus padding (or its natural width if smaller), and the remaining width is shared in proportion to what each column still needs; if those minimums exceed the column, the columns keep their natural widths and the table becomes the framed, horizontally scrolling figure. Cell text never breaks inside a word |
| PAG-07 | Images, display math and front matter cards are atomic. They scale to fit the column width and at most 85% of page height. If an atomic block does not fit the remaining space, it moves to the next page and the remaining space stays empty (no reflow of later text above it in v1) |
| PAG-08 | List items split like paragraphs; the marker stays with the first line |
| PAG-09 | Pagination MUST be **anchored and lazy**: lay out the spread containing the anchor first, then the neighbours (at least ±2 spreads), then the rest in the background. The total page count is estimated until the background pass finishes, and the estimate is visibly marked as approximate in progress UI only if it changes by more than 5%. The provisional window starts at the anchor line and may break pages differently from the canonical pagination; the invariant is that the anchor is on the first provisional page and on the spread returned for it in the canonical and complete runs |
| PAG-10 | A layout cache MUST key on (document content hash, geometry hash). Returning to a previous geometry (fold then unfold) reuses it |
| PAG-11 | Page identity is the anchor of its first slice, never an integer index alone |
| PAG-13 | With columns of unequal width (LAY-12), a block that fits whole in the column where it starts is set at that column's width. A block that splits across columns is set at the width of the narrowest column of the spread, in every slice, so a break always falls on a line boundary of the layout that follows. Text of such a block may therefore be narrower than the wider column |
| PAG-12 | A spread advances by `cols` pages. The first page of the document starts in the leftmost column (left-to-right documents). The final spread may contain empty columns; the last page ends with a small end ornament |

---

## 6. Reading position and continuity

| ID | Requirement |
|---|---|
| ANC-01 | The current anchor is the first visible character of the first column in reading order (paged: leftmost for left-to-right documents, rightmost for right-to-left documents) or of the topmost visible line (scroll) |
| ANC-02 | The anchor MUST be written to `SavedStateHandle` on every settled page turn or scroll stop, and persisted per document URI (not per content hash, so a position survives edits made in another app) at most once per second and on `onStop` |
| ANC-03 | On any geometry change (fold, unfold, posture change, rotation, resize, font, margin, column setting, theme metric change): capture the anchor and its on-screen line rectangle, re-paginate around the anchor, show the spread containing it, then run the continuity motion (MOT-05). The anchor MUST be visible in some column of the new spread, not necessarily the same column it was in. After a geometry change the stored anchor is kept unchanged, so repeated fold and unfold cannot drift the reading position. ANC-01 applies again only when the reader next turns a page or the scroll settles |
| ANC-04 | Switching between paged and scroll modes MUST preserve the anchor |
| ANC-05 | Reopening a document restores its last anchor. If the file changed since (content hash differs), keep the stored anchor and clamp it to the new document: a stored block index past the last block becomes the last block with offset 0, a stored offset past the end of its block becomes the length of that block, and nothing alarming is shown |
| ANC-06 | A "return" chip appears after any jump (table of contents, link, search result, footnote) and returns to the pre-jump anchor. It survives geometry changes. It disappears after 8 seconds of reading or on tap |
| ANC-07 | The Activity SHOULD handle `screenSize`, `smallestScreenSize`, `screenLayout` and `orientation` configuration changes itself so the reader recomposes in place; process death and recreation MUST still restore the anchor exactly |

---

## 7. Navigation and input

| ID | Requirement |
|---|---|
| NAV-01 | Paged mode: horizontal swipe turns spreads, following finger velocity |
| NAV-02 | Tap zones on the reading surface: outer 30% left goes back, outer 30% right goes forward, center toggles chrome. Mirrored for RTL documents. In tabletop posture the zones stay active over the text region and the control deck repeats previous and next as large zones (POS-04) |
| NAV-03 | Keyboard: Right, Page Down, Space, J go forward; Left, Page Up, Shift+Space, K go back; Home and End jump; Ctrl+F opens search; T opens table of contents; Escape closes overlays |
| NAV-04 | Volume keys turn pages when enabled in settings (off by default) |
| NAV-05 | Scroll mode: standard fling, plus the same keyboard shortcuts scrolling by 90% of the viewport |
| NAV-06 | Predictive back: from reader to library animates the reader shrinking toward its library item; from overlays closes the overlay |
| NAV-07 | Internal links jump to the target heading or footnote and trigger ANC-06 |
| NAV-08 | External links show a small confirmation sheet with the full URL, then open in the default browser through a plain `ACTION_VIEW` intent. Bifolio itself performs no network request |
| NAV-09 | Long press selects text; selection supports copy and share. Cross-page selection is NOT required in v1 |

---

## 8. Typography and themes

| ID | Requirement |
|---|---|
| TYP-01 | Reading fonts bundled (subset, OFL-1.1, see ADR-0009): Literata (default), Source Serif 4, Atkinson Hyperlegible Next. Code font: JetBrains Mono. No downloadable fonts in v1 |
| TYP-02 | Body size default 18sp; range 14 to 28sp in 1sp steps; respects system font scale including nonlinear scaling |
| TYP-03 | Line height default 1.5; options 1.35, 1.5, 1.65 |
| TYP-04 | Line breaking: `LineBreak.Paragraph` for body, `LineBreak.Heading` for headings. On columns narrower than `45 × charW`, use the balanced strategy |
| TYP-05 | Hyphenation: on by default when the document language is known (front matter `lang`, else app locale); uses platform hyphenation |
| TYP-06 | Justification: off by default. When on, it applies only if hyphenation is on and the measure is at least `45 × charW`; otherwise text stays ragged-right |
| TYP-07 | Heading scale (relative to body): H1 1.75, H2 1.45, H3 1.22, H4 1.08 bold, H5 1.0 bold small caps, H6 0.92 bold muted. Space above headings 1.4 lines, below 0.5 lines |
| TYP-08 | Four reading themes with fixed palettes (exact values in `docs/design/DESIGN.md`): Paper, Sepia, Dusk, Night (true black). Default follows system dark mode: Paper or Dusk |
| TYP-09 | Reading surface colors MUST be the fixed palette. Dynamic color MAY be used for library and settings chrome only, behind a setting (off by default) |
| TYP-10 | Body text contrast at least 7:1 and secondary text at least 4.5:1 in every theme |

---

## 9. Screens and UI

| ID | Requirement |
|---|---|
| UI-01 | **Library** (start screen): an "Open document" action, recents with title, file name, last read time and progress, swipe or menu to remove. Empty state explains in one sentence and shows the open action |
| UI-02 | On windows 840dp and wider, library and reader MAY show side by side (list-detail) when no document is in immersive reading |
| UI-03 | **Reader chrome** (shown on center tap): top bar with title, table of contents, search, appearance; bottom bar with progress scrubber, page label ("12 of 87" or "12 to 13 of 87" on spreads) and time remaining. Chrome auto-hides after 8 seconds without use, and any use restarts the countdown |
| UI-04 | Immersive reading hides system bars; they reappear with the chrome |
| UI-05 | **Progress hairline**: a 2dp line at the foot of the spread showing document progress; can be turned off |
| UI-06 | **Time remaining**: words remaining in document and in current section divided by reading speed (default 230 words per minute, calibrated from the user's actual page-turn pace after 10 pages, clamped 120 to 450) |
| UI-07 | **Table of contents**: sheet on compact widths, side pane over the reading surface at 840dp and wider. Current section highlighted. Levels H1 to H3 shown, deeper collapsed |
| UI-08 | **Search**: query field, result list with context snippets, highlights in text, next and previous. Results computed off the main thread over the source text and mapped to anchors. YAML front matter (SYN-13) is not searched |
| UI-09 | **Footnote popover**: anchored to the marker, scrollable if long, with a "go to note" action |
| UI-10 | **Appearance sheet**: font, size, line height, margins, justification, hyphenation, theme, reading mode (paged or scroll), columns (Auto, 1, 2, 3, with a note when the current window limits the choice), page turn animation (Slide, Fade, None), progress hairline, volume keys |
| UI-11 | **About**: version, license (Apache-2.0), open source licenses screen generated from dependencies, link text for the source repository and `bifolio@dcesares.dev` |
| UI-12 | Icons: Lucide, outlined, 1.5dp stroke at 24dp (ADR-0009). No other icon set |
| UI-13 | Every user-facing string MUST live in string resources (English in v1) |
| UI-14 | Relative image folder grant: when a document references relative images and no grant exists, show one inline banner ("Show images from this folder") that opens the folder picker at the document's location when possible. The banner reserves its height above the page area and never covers text. A refusal applies to every document in the same folder |
| UI-15 | **Recent preview**: each recent shows a one-line preview (at most 140 characters) of the first paragraph of the document, taken from the parsed model when the document is opened and stored with the recent. The library never reads or parses a document to build the list. Documents without a paragraph show no preview line |
| UI-16 | **Unavailable recent**: when opening a recent fails because the file moved, was deleted or access was revoked, the recent is marked unavailable, shown dimmed with the label "Unavailable" and a remove action, and is kept until the user removes it. A later successful open clears the mark. The library never checks files while listing |

---

## 10. Motion and haptics

| ID | Requirement |
|---|---|
| MOT-01 | Two motion families (ADR in `docs/design/DESIGN.md`): **reading motion** uses Material 3 `MotionScheme.standard()` springs with no overshoot; **chrome motion** uses `MotionScheme.expressive()` |
| MOT-02 | Page turn **Slide** (default): spread moves as one unit following the finger, settling with a non-bouncy spring. The incoming spread's outer page leads by a subtle 8% parallax |
| MOT-03 | Page turn **Fade**: 180ms crossfade. Page turn **None**: instant |
| MOT-04 | Library to reader: container transform from the recent item to the reader. Back reverses it, driven by predictive back progress |
| MOT-05 | **Continuity motion** (the signature): after re-pagination for a geometry change, the anchor line animates from its previous screen rectangle to its new one over about 250ms, and a soft highlight on that line fades over 600ms |
| MOT-06 | Chrome enters with an expressive spring (slight overshoot allowed); exits faster than it enters |
| MOT-07 | When system "Remove animations" is on, or the in-app reduce motion option is on, all motion becomes Fade or None, and MOT-05 becomes the highlight only |
| MOT-08 | Haptics only for: crossing a section boundary while scrubbing (tick), reaching document start or end (light), confirming an external link (confirm). None on ordinary page turns |
| MOT-09 | No animation MAY block input; every animation is interruptible |

---

## 11. Accessibility

| ID | Requirement |
|---|---|
| A11Y-01 | TalkBack reads page content in order with headings marked as headings; a "read continuously" action advances pages automatically |
| A11Y-02 | All interactive targets at least 48dp |
| A11Y-03 | Font scaling up to 200% stays usable: auto columns reduce, chrome reflows, nothing clips |
| A11Y-04 | Images expose alt text; math exposes its TeX source as content description; code blocks announce language |
| A11Y-05 | Reduce motion per MOT-07 |
| A11Y-06 | Full keyboard operation per NAV-03, with visible focus indicators in chrome |

---

## 12. Privacy, security, permissions

| ID | Requirement |
|---|---|
| PRV-01 | The merged manifest MUST NOT contain `android.permission.INTERNET` or any network permission. CI enforces it |
| PRV-02 | No analytics, crash reporting, ads or tracking libraries. No Google Play Services dependency |
| PRV-03 | No WebView class may be referenced by app code |
| PRV-04 | Only URIs the user granted are read. Folder grants are stored and listed in settings with a revoke action |
| PRV-05 | `android:allowBackup="false"`: nothing leaves the device through Android backup or device transfer (owner decision 2026-10-23, stricter than the earlier "preferences and recents only") |
| PRV-06 | Play Data safety: "No data collected, no data shared". Privacy policy text lives in `docs/PRIVACY.md` |

---

## 13. Performance budgets

Measured with Macrobenchmark on a mid-range reference phone (to be fixed at M0, for example a Pixel 7a class device) and on the owner's foldable.

| ID | Budget |
|---|---|
| PRF-01 | Cold start to library interactive: under 400ms (P50) |
| PRF-02 | Open a 100 KB document to first rendered spread: under 500ms (P50), under 800ms (P90) |
| PRF-03 | Page turn: zero janky frames at 120Hz on the reference foldable in a 50-turn benchmark |
| PRF-04 | Re-pagination of the visible spread after a geometry change: under 100ms; full background pagination of a 1 MB document: under 3s, never on the main thread |
| PRF-05 | Memory: under 150 MB heap for a 1 MB document with images |
| PRF-06 | Download size per ABI: under 8 MB |
| PRF-07 | Baseline profile and startup profile shipped; R8 full mode and resource shrinking enabled for release |

---

## 14. Quality gates

| ID | Gate |
|---|---|
| QA-01 | `:core:*` modules are pure Kotlin/JVM where possible and covered by unit tests; the paginator and geometry math have property-based tests |
| QA-02 | Screenshot tests (Roborazzi) cover the device matrix in `docs/TESTING.md` for every reader state in the test corpus |
| QA-03 | Instrumented tests on Gradle Managed Devices with foldable profiles cover fold, unfold and posture transitions keeping the anchor on screen (ANC-03) |
| QA-04 | CI fails on: test failure, lint or detekt errors, screenshot diff not approved, INTERNET permission present (PRV-01), WebView reference (PRV-03), dependency with a license outside the allow list in ADR-0001 |
| QA-05 | Every pull request cites spec IDs and states how each was verified |

---

## 15. Open items to resolve during M0

These are known unknowns. Each must be closed with an ADR or a spec edit before the milestone that depends on it.

| ID | Item | Needed by |
|---|---|---|
| OPN-01 | Pin current stable versions: Kotlin, AGP, Compose BOM, Material 3, Material 3 Adaptive, WindowManager, commonmark-java, Roborazzi (images use the platform decoder, no image library) | M0 |
| OPN-02 | Confirm license and maturity of the math library (huarangmeng/latex, MIT) and its fallback RaTeX; spike rendering of 20 corpus formulas | M5 |
| OPN-03 | Confirm license of each bundled font from its upstream repository and record in `docs/design/FONTS.md` | M4 |
| OPN-04 | Confirm a Compose-compatible Lucide icon source with ISC license, or import the needed SVGs as vector drawables | M4 |
| OPN-05 | Confirm `TextMeasurer` off-main-thread safety on the pinned Compose version; otherwise measure through `StaticLayout` on a background thread | M2 |
| OPN-06 | Choose reference mid-range device for PRF budgets | M0 |
