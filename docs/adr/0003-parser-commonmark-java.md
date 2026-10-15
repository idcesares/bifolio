# ADR-0003: Parser: commonmark-java

Status: Accepted
Date: 2026-10-15
Spec IDs: DOC-06, DOC-07, SYN-01 to SYN-15

## Context

The parser must be spec-compliant, maintained, fast on the JVM, provide source positions, and support GFM extensions: tables, task lists, strikethrough, autolinks, footnotes, alerts, YAML front matter, heading anchors.

## Decision

Use **commonmark-java** (BSD-2-Clause) with its official extensions. The parser output is converted immediately into Bifolio's own immutable block model in `:core:model`; no commonmark types leak beyond `:core:parse`.

Inline math (`$...$`) and alerts are handled by small Bifolio extensions if the official extensions do not cover them exactly as specified.

## Consequences

- The rest of the app depends only on Bifolio's model, so the parser can be replaced later.
- Parsing runs on `Dispatchers.Default`; for files above 2 MB, blocks are emitted progressively.

## Alternatives considered

- **JetBrains intellij-markdown** (Apache-2.0): viable, multiplatform; less direct GFM extension coverage for our needs.
- **flexmark-java**: stale since 2023.
- **Rust parsers via JNI**: faster, but adds native libraries per ABI and build complexity. Revisit only if profiling shows parsing is a bottleneck, which is unlikely; layout dominates.
