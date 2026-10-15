# ADR-0007: Code highlighting behind an adapter

Status: Accepted
Date: 2026-10-15
Spec IDs: SYN-05, SYN-11

## Context

Options range from heavy TextMate engines to light tokenizers. Prism4j (used by older Android Markdown apps) has not been released since 2019.

## Decision

`:core:highlight` exposes an interface: tokenize source text for a language into typed spans (keyword, string, number, comment, type, function, operator, punctuation, plain). The first implementation uses a light Apache-2.0 or MIT library (candidate: dev.snipme Highlights) or a small in-house tokenizer for the languages in SYN-11. Tokens are computed off the main thread and cached per block.

Colors come from `core:designsystem` per theme, never from the library.

## Consequences

- Highlighting quality for rare languages is modest in v1; unknown languages render plain.
- A heavier engine (Eclipse TM4E, EPL-2.0) can replace it later through a new ADR.
