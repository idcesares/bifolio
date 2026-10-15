# ADR-0006: Math rendering behind an adapter

Status: Accepted (library choice pending OPN-02)
Date: 2026-10-15
Spec IDs: SYN-12, A11Y-04

## Context

The common Android LaTeX library (JLatexMath and its Android port) is archived and GPL-licensed, so it is excluded by ADR-0001. Newer native options exist but are young.

## Decision

`:core:math` exposes an interface: render TeX at a font size and color, returning a size, a baseline and a drawable or image bitmap, or a failure. The first implementation uses **huarangmeng/latex** (MIT, Compose). **RaTeX** (MIT, KaTeX-compatible core) is the fallback candidate.

OPN-02 must confirm license, maturity and rendering quality on 20 corpus formulas before M5.

## Consequences

- Rendering failures degrade to monospace TeX source, never a crash.
- Rendered formulas are cached per (TeX, size, color).
- The renderer can be swapped without touching the paginator.

## Alternatives considered

- MathJax or KaTeX in a WebView: excluded by ADR-0002.
- JLatexMath: excluded by ADR-0001.
