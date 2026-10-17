# ADR-0005: Reading position as an anchor

Status: Accepted
Date: 2026-10-15
Spec IDs: ANC-01 to ANC-07, PAG-11, MOT-05

## Context

On a foldable, the same document has different page counts on the cover screen, unfolded, in book posture, at different font sizes and in split screen. A page number is meaningless across these.

## Decision

The only persisted reading position is the **anchor** `(blockIndex, charOffset)`. Pages and spreads are derived views. Page identity is the anchor of its first slice. Every geometry change re-paginates around the anchor and shows the spread containing it.

## Consequences

- Fold and unfold never lose the reader's place, and the continuity motion (MOT-05) has a precise target.
- The anchor uses source offsets but the measurer sees rendered text. `core:parse` must expose, per inline, the source range of the rendered text, and `core:layout` maps lines to source offsets through those ranges. Until then `OffsetMap` is an interim, monotonic mapping (follow-up before the paged reader ships).
- Progress and "page X of Y" are computed for the current geometry only and never stored.
- The anchor is stored per document URI, not per content hash, so the position survives edits made in another app. On reopen it is clamped to the document: block index to the last block, character offset to the length of that block (ANC-02, ANC-05).
- The progress shown in recents (UI-01) is a fraction of the source text, derived from the anchor (source offset over source length). It is independent of geometry and is not a page number, so it may be stored.

## Alternatives considered

- Page index plus page count ratio: drifts with geometry; rejected.
- Scroll offset in pixels: depends on width and font; rejected.
