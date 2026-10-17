# ADR-0013: Text measurer on StaticLayout, off the main thread

Status: Accepted
Date: 2026-10-17
Spec IDs: OPN-05, TYP-04, TYP-05, TYP-06, PAG-01, PRF-04

## Context

`:core:layout` paginates through the `BlockMeasurer` interface (ADR-0004) and runs on `Dispatchers.Default`.
OPN-05 asks whether Compose `TextMeasurer` is safe off the main thread on the pinned version (ui-text 1.12.1,
Compose BOM 2026.09.00), and otherwise to measure with `StaticLayout` on a background thread.

What was checked in this work, and what was not:

- Checked: the pinned `ui-text` jar exposes `LineBreak.Simple`, `Heading` and `Paragraph` (read with `javap`).
  `StaticLayout` runs in a Robolectric test from eight threads with identical results.
- Not checked: the thread-safety guarantees of `TextMeasurer` (its internal cache, `Density` and font
  resolver capture) were not confirmed against the official documentation or on a device. The numeric values
  behind `LineBreak.Paragraph` and `Heading` were not read, and the mapping below to `StaticLayout`
  strategies is from memory of their documented intent (paragraph = quality, heading = balanced).
- Not checked: output on a real device or emulator. Robolectric `NATIVE` graphics mode uses the real native
  text engine, but fonts there are the JVM host's, so pixel widths are not device widths.

## Decision

`:core:text` implements `BlockMeasurer` with `StaticLayoutBlockMeasurer`, built on `android.text.StaticLayout`.
We do not depend on `TextMeasurer` being thread-safe, so OPN-05 is closed on the `StaticLayout` branch of its
own wording. If a later change wants `TextMeasurer` (for exact parity with `Text`), it must first prove
thread-safety and add a parity test against this class.

Rules:

1. Each call creates its own `TextPaint` and `StaticLayout`. The class holds only immutable configuration
   (`TextStyleConfig`), so it is safe to share and to call from any background thread. No call touches a
   `View` or the main looper.
2. TYP-04: `BREAK_STRATEGY_HIGH_QUALITY` for body text; `BREAK_STRATEGY_BALANCED` for headings and for any
   measure under `45 x charW`; `BREAK_STRATEGY_SIMPLE` for code (no balancing of code lines).
3. TYP-05: `HYPHENATION_FREQUENCY_NORMAL` for body text when enabled, with the document locale set on the
   paint (`TextStyleConfig.locale`: front matter `lang`, else app locale). Code, headings and table cells are
   not hyphenated.
4. TYP-06: `JUSTIFICATION_MODE_INTER_WORD` only for body text when the setting is on, hyphenation is on and
   the measure is at least `45 x charW`; otherwise ragged-right.
5. Contract fixes on top of `StaticLayout`: empty text yields zero lines; lines with no characters (the one
   after a final `\n`, or a width too small for a glyph) are dropped, which keeps the lines tiling the text.
   Width is clamped to at least 1 px.
6. `intrinsicWidth` is the widest `\n`-separated line by `Paint.measureText`. `measureAtomic` is a 16:9
   placeholder until images and math are measured for real; the paginator scales atomic blocks (PAG-07).
7. `BlockMeasurer` has no locale, `charW` or justify parameters, so they come through the constructor. A
   change of any of them needs a new instance and invalidates the layout cache (ADR-0004).

Fonts are system fonts until M4 (ADR-0009): serif body, monospace code. Roles scale the body size per
TYP-07 (H1 1.75, H2 1.45, H3 1.22, H4 1.08, H5 1.0, H6 0.92, code 0.88, table 0.9) and line height is a
factor of the font size (body from settings, headings 1.2 to 1.4, code 1.45), applied with
`setLineSpacing(extra, 1f)`.

### Amendment (M2): the reader draws what was measured

8. `measureLines` and `intrinsicWidth` receive the `TextStyles` of the text (bold, italic, code, scripts,
   links), because styles change widths. The reader never lets Compose break text in paged mode.
9. `TextLayoutFactory` in `:core:text` is the single recipe for a layout. The measurer calls it to count
   lines; the reader calls it with the same text, role, width and styles, plus a palette (colors never change
   metrics), and draws the line range of each `TextLeaf` with a clip and a translate. Substrings are never
   drawn on their own, which would lose hyphens and styles.
10. `PageDetailer` in `:core:layout` converts a `Page` into drawing instructions (`PageDraw`) off the main
    thread, so a page turn measures nothing.

## Consequences

- Pagination never waits for the main thread.
- Tests run on the JVM with Robolectric (`@GraphicsMode(NATIVE)`, JUnit 4 through the vintage engine). In the
  default legacy graphics mode `StaticLayout` does not break lines realistically, so those tests fail there
  by design: do not drop the annotation.
- Measured widths in tests are host widths. Device accuracy and PRF-04 timing still need the emulator check
  listed for M2 in the roadmap.
- Text drawn by Compose `Text` may break differently from `StaticLayout` in rare cases (different
  hyphenation or fallback fonts). The reader must draw each line from the measured line ranges, not re-break
  the text, so the pages match what the paginator computed. This is a requirement on M2 UI work.
- Robolectric and JUnit 4 become test dependencies of `:core:text` (already allowed by ADR-0012).

## Alternatives considered

- Compose `TextMeasurer` on a background thread: no verified thread-safety guarantee here (see Context).
- Measure on the main thread: violates the performance rules.
- Per-role cached `TextPaint`: cheaper, but `Paint` is mutable and not documented as thread-safe.
