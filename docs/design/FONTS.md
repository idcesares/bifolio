# Bundled fonts (OPN-03, TYP-01)

Source: the `google/fonts` repository, `ofl/` directory, fetched 2026-10-18. Each family ships its own `OFL.txt`
next to the font files in `core/designsystem/src/main/assets/fonts/`. License of all four: SIL Open Font License 1.1
(allowed by ADR-0001, no attribution UI beyond the licenses screen, which lists them).

| Family | Role | Files | Copyright line (from the shipped OFL.txt) |
|---|---|---|---|
| Literata | Body default | `Literata.ttf`, `Literata-Italic.ttf` (variable `opsz`, `wght`) | 2017 The Literata Project Authors |
| Source Serif 4 | Body alternative | `SourceSerif4.ttf`, `SourceSerif4-Italic.ttf` (variable `opsz`, `wght`) | see `OFL-sourceserif4.txt` |
| Atkinson Hyperlegible Next | Body accessible | `Atkinson.ttf`, `Atkinson-Italic.ttf` (variable `wght`) | 2020-2024 The Atkinson Hyperlegible Next Project Authors |
| JetBrains Mono | Code | `JetBrainsMono.ttf` (variable `wght`) | see `OFL-jetbrainsmono.txt` |

## Subsetting

Made with `pyftsubset` (fonttools), keeping the variable axes and the layout features `kern liga calt onum lnum smcp
c2sc frac ccmp locl mark mkmk`. Code points: Basic Latin, Latin-1 Supplement, Latin Extended-A/B, Greek, Cyrillic,
Latin Extended Additional, General Punctuation, euro sign, arrows up and down, minus, bullet, ellipsis.
Raw size 3.4 MB; the APK compresses it. PRF-06 (under 8 MB per ABI) is checked in the release build.

Note: the OFL reserved font names apply to modified versions. The subsets keep the original family names and are
not redistributed outside the app, which the OFL allows for embedding.

## Use

- Paged mode: `AssetFontSet` (`core:text`) loads the files with `Typeface.Builder` and a `wght` variation.
- Scroll mode and UI: `ReadingFont.family` (`core:designsystem`) builds the Compose `FontFamily`.
- The user picks the body family in the appearance sheet; code is always JetBrains Mono.

## Math fonts (ADR-0006)

The KaTeX math fonts come with `io.github.huarangmeng:latex-fonts-katex:1.6.1` and are loaded by `:core:math` from the
library asset path, not from `core/designsystem`. They are SIL Open Font License 1.1 (the font metadata; upstream
labels the package MIT) and are listed in NOTICE. The license text and the copyright holders (Design Science, Inc. and Khan Academy, read from the name tables of the font files) are in `core/designsystem/src/main/assets/fonts/OFL-katex.txt`, next to the other `OFL-*.txt`. The in-app licenses screen is M6 and is still pending; until then NOTICE and that file carry the attribution. They draw formulas only and are never used for text.
