# ADR-0007: Code highlighting behind an adapter

Status: Accepted
Date: 2026-10-15
Spec IDs: SYN-05, SYN-11

## Context

Options range from heavy TextMate engines to light tokenizers. Prism4j (used by older Android Markdown apps) has not been released since 2019.

## Decision

`:core:highlight` is a pure Kotlin/JVM module that exposes `CodeHighlighter.tokenize(source, language): List<TokenSpan>`. Spans carry a typed kind (keyword, string, number, comment, type, function, operator, punctuation, plain), are sorted, do not overlap, stay within bounds and together cover the whole source. Unknown or null languages return a single plain span. Tokens are computed off the main thread and cached per block.

The implementation is in-house (`RuleBasedHighlighter`): a shared hand-written scanner driven by small per-language rule sets (keywords, types, comment and string delimiters, key markers), plus dedicated scanners for html, diff and markdown. It covers every language in SYN-11 with aliases (kt, js, ts, sh, zsh, yml, c++, py, rs, htm, and others). Rationale: no maintained, light, permissively licensed library fits (Prism4j was last released in 2019), a library would add a dependency to review against ADR-0001, and the in-house scanners add zero dependencies and no license risk. Every scanner is a single forward pass with O(1) lookahead per token and no regular expressions, so very long lines and unclosed strings or comments cannot cause super-linear time; this is covered by a test with million-character inputs.

Colors come from `core:designsystem` per theme, never from the library.

## Consequences

- Highlighting quality is approximate in v1 (lexical only: no nesting, no semantic analysis, e.g. nested Kotlin block comments, JS regex literals and string templates are not parsed); unknown languages render plain.
- Adding a language means adding one rule set; swapping the engine only requires another `CodeHighlighter`.
- A heavier engine (Eclipse TM4E, EPL-2.0) can replace it later through a new ADR.
