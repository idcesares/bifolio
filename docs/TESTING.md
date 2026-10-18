# Testing

Implements ADR-0011. Spec IDs: QA-01 to QA-05.

## 1. Verification ladder

| Level | Tool | Runs where | What it proves |
|---|---|---|---|
| 1 | JUnit + property tests (kotest-property or jqwik) | JVM, every commit, Stop hook | Parsing, geometry, pagination, anchors obey SPEC rules |
| 2 | Roborazzi screenshot tests (Robolectric) | JVM, every PR | Every reader state renders correctly across the device matrix |
| 3 | Instrumented tests on Gradle Managed Devices | CI emulators with foldable profiles | Fold, unfold, posture changes, predictive back, process death keep the anchor |
| 4 | Macrobenchmark | Real device | PRF budgets |
| 5 | Owner review | Owner's foldable | Motion feel, typography, signature moments (milestones M1, M4, M6) |

## 2. Device matrix (screenshot and emulator tests)

Sizes are window sizes in dp. The foldable inner screen is the owner's real device: 2256 x 2504 px at 480 dpi, about 752 x 835 dp (density 3). Folds are injected with the WindowManager testing artifact.

| ID | Profile | Window (w x h dp) | Fold | Expected layout |
|---|---|---|---|---|
| DM-01 | Phone portrait | 412 x 915 | none | 1 column |
| DM-02 | Phone landscape | 915 x 412 | none | Auto (1 column at default size) |
| DM-03 | Foldable cover screen | 360 x 800 | none | 1 column |
| DM-04 | Foldable inner, flat, no separating fold | 752 x 835 | vertical, FLAT, not separating | Auto (1 column at default size), text may cross the fold (LAY-14 is for separating folds only) |
| DM-05 | Foldable inner, book posture | 752 x 835 | vertical, HALF_OPENED | 2 columns, gutter on fold |
| DM-06 | Foldable inner, hinge occludes pixels | 752 x 835 | vertical, FLAT, occlusion FULL, 24dp wide | 2 columns, gutter covers hinge plus margins |
| DM-07 | Foldable tabletop | 752 x 835 | horizontal, HALF_OPENED | Text top, control deck bottom |
| DM-08 | Wide 4:3 foldable inner | 960 x 720 | vertical, FLAT | Auto (1 column at default size), text may cross the fold (LAY-14 is for separating folds only) |
| DM-09 | Tablet / tri-fold | 1280 x 900 | none | Auto 2 or 3 |
| DM-10 | Narrow multi-window | 320 x 640 | none | 1 column, chrome usable |
| DM-11 | Split screen on unfolded device, fold outside window | 420 x 900 | fold does not intersect | 1 column, fold ignored |
| DM-12 | Font scale 2.0 on DM-05 | 752 x 835 | book | Columns reduce per LAY-10 (1 column on the wider side, centered in the window or slid clear of the band), nothing clips |

Each matrix entry is rendered in Paper and Night themes at minimum, plus RTL for DM-01 and DM-05.

## 3. Test corpus

Location: `testdata/corpus/`. Every document is written for Bifolio from scratch (clean room). Files are small unless noted.

| ID | File | Exercises |
|---|---|---|
| C-01 | `prose-short.md` | Paragraphs, emphasis, links |
| C-02 | `prose-long.md` (about 60k words, produced by a seeded text generator in test code) | Pagination at scale, PRF-04 |
| C-03 | `headings-all-levels.md` | SYN-01, PAG-03 keep-with-next |
| C-04 | `headings-non-latin.md` | Anchors for Portuguese accents, Greek, Cyrillic, CJK |
| C-05 | `lists-nested.md` | SYN-03, PAG-08 |
| C-06 | `tasks.md` | Read-only task glyphs |
| C-07 | `quotes-and-callouts.md` | SYN-04, SYN-10, PAG-05 |
| C-08 | `code-languages.md` | All SYN-11 languages |
| C-09 | `code-long-block.md` | A 300-line block crossing several pages, PAG-04 |
| C-10 | `code-long-lines.md` | Wrap versus horizontal scroll |
| C-11 | `tables-simple.md` | Alignment |
| C-12 | `tables-tall.md` | Header repetition across pages, PAG-06 |
| C-13 | `tables-wide.md` | Framed horizontal scroll |
| C-14 | `math-inline-display.md` | 20 formulas from simple to hard (OPN-02 spike) |
| C-15 | `math-errors.md` | Invalid TeX fallback |
| C-16 | `images-relative/` (folder with images) | SYN-08, UI-14 folder grant |
| C-17 | `images-oversized.md` | PAG-07 scaling and push-to-next-page |
| C-18 | `footnotes.md` | SYN-09, UI-09 |
| C-19 | `front-matter.md` | SYN-13, `lang` drives hyphenation |
| C-20 | `html-inline.md` | DOC-09 safe subset and plain-text fallback |
| C-21 | `mermaid.md` | SYN-14 label |
| C-22 | `widows-orphans.md` | Crafted paragraphs that land exactly at page boundaries |
| C-23 | `deep-nesting.md` | DOC-08 nesting cap |
| C-24 | `crlf.md` (CRLF line endings) | DOC-04 |
| C-25 | `utf16-bom.md` | DOC-03 |
| C-26 | `windows-1252.md` | DOC-03 fallback notice |
| C-27 | `empty.md` and `whitespace-only.md` | Empty states |
| C-28 | `huge-9mb.md` (generated at test time, not committed) | DOC-05 scroll-only path |
| C-29 | `rtl-arabic.md` | Mirrored tap zones, RTL layout |
| C-30 | `ai-output-sample.md` | Typical assistant output: headings, lists, code, tables, math mixed |

## 4. Rules

- Screenshot baselines change only in commits that explain the intended visual change.
- Property tests run with a fixed seed in CI and a random seed locally; failing seeds are added as regression cases.
- A spec ID is "verified" only when a test fails if the rule is broken.
