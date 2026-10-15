# ADR-0004: Own paginator over a text-measuring interface

Status: Accepted
Date: 2026-10-15
Spec IDs: PAG-01 to PAG-12, LAY-04 to LAY-14, PRF-04

## Context

No available library paginates rich Markdown into columns while respecting widows, orphans, keep-with-next, splittable code and tables, atomic images and a physical fold. This is Bifolio's core differentiator.

## Decision

1. `:core:layout` contains pure Kotlin geometry and pagination code that depends on a small interface:
   - measure a text block at a width and return line count, per-line top, bottom and character range;
   - measure atomic blocks (image, math) as a size.
2. `:core:text` implements the interface with Compose `TextMeasurer` (or `StaticLayout` if OPN-05 shows `TextMeasurer` is not safe off the main thread).
3. Pagination output is immutable: `Page(slices)`, `Slice(blockIndex, startLine, endLine)`.
4. Pagination is **anchored and lazy**: the spread containing the anchor first, neighbours next, the rest in the background with cancellation when geometry changes.
5. Layout results are cached by (content hash, geometry hash).
6. Rendering draws precomputed layouts; it never re-measures during a page turn.

## Consequences

- Geometry and pagination rules are testable on the JVM in milliseconds with a fake measurer, including property-based tests.
- Cross-page text selection is not supported in v1 (NAV-09).

## Alternatives considered

- CSS paged media in a WebView: rejected by ADR-0002.
- Paginating a `LazyColumn` by viewport height: cannot satisfy widow, orphan or fold rules.
