# ADR-0006: Math rendering behind an adapter

Status: Accepted (library chosen 2026-10-21, OPN-02 closed)
Date: 2026-10-15, updated 2026-10-21
Spec IDs: SYN-12, A11Y-04

## Context

The common Android LaTeX library (JLatexMath and its Android port) is archived and GPL-licensed, so it is excluded by ADR-0001. Newer native options exist but are young.

## Decision

`:core:math` exposes an interface: render TeX at a font size and color, returning a size, a baseline and a drawable or image bitmap, or a failure. The first implementation uses **huarangmeng/latex** (MIT, Compose). **RaTeX** (MIT, KaTeX-compatible core) is the fallback candidate.

OPN-02 must confirm license, maturity and rendering quality on 20 corpus formulas before M5.

### Decision of 2026-10-21 (OPN-02)

The owner approved `io.github.huarangmeng:latex-renderer:1.6.1` and `io.github.huarangmeng:latex-fonts-katex:1.6.1`, on the evidence in [ADR-0006-math-spike-result.md](ADR-0006-math-spike-result.md), with its 11 mandatory adapter guards as part of the decision. The owner also accepted the known `cases` defect (columns swapped, an injected "if"). The adapter mitigates it by rewriting `cases` to a two column `array` inside a left brace, and writes `-` in math mode as U+2212. RaTeX remains the documented fallback.

Adapter behavior that is implemented but not yet a decision (open items for the owner and the SYN-12 and PAG-07 wording):

- PAG-07 is implemented as written: fit the column width, at most 85% of the page height, never scaled up. There is no floor. The 60% floor proposed in the spike stays open.
- Macro definitions (`\def`, `\newcommand`, `\input`) and `%` comments are rejected as failures by the pre-validation.
- Known and not yet decided: `aligned` centers its left column, and the right parenthesis of `pmatrix` looks slightly clipped.

### Dependencies

Only `:core:math` declares the two approved artifacts. They add 41 shipped artifacts (160 to 201 in `checkPolicy`), all Apache-2.0 or MIT. The JetBrains Compose Multiplatform runtime and the androidx savedstate and lifecycle artifacts are Apache-2.0 and allowed as transitive dependencies of an approved library (ADR-0001).

| Artifact | Version | License | Role |
|---|---|---|---|
| io.github.huarangmeng:latex-renderer, latex-parser, latex-base (and their `-android` variants) | 1.6.1 | MIT | TeX parsing, layout and drawing |
| io.github.huarangmeng:latex-fonts-katex (and `-android`) | 1.6.1 | MIT code, KaTeX fonts SIL OFL-1.1 | Math fonts, injected before the first measurement |
| org.jetbrains.compose.ui, foundation, foundation-layout, animation, animation-core, runtime, runtime-saveable, annotation-internal, collection-internal | 1.10.3 | Apache-2.0 | Transitive Compose Multiplatform runtime |
| org.jetbrains.compose.components:components-resources (and `-android`) | 1.10.3 | Apache-2.0 | Transitive, font resources |
| org.jetbrains.compose.ui:ui-backhandler (and `-android-debug`) | 1.9.1 | Apache-2.0 | Transitive |
| org.jetbrains.compose.material3:material3, org.jetbrains.compose.material:material-ripple | 1.9.0, 1.9.1 | Apache-2.0 | Transitive Material code, not icons (UI-12 is not affected); not used by the app |
| org.jetbrains.androidx.lifecycle (common, runtime, runtime-compose, viewmodel, viewmodel-savedstate), org.jetbrains.androidx.savedstate (savedstate, savedstate-compose) | 2.9.6, 1.3.6 | Apache-2.0 | Transitive |
| androidx.savedstate (savedstate, -android, -ktx, compose, compose-android) | 1.3.3 | Apache-2.0 | Transitive |

### Implementation notes (2026-10-21, after review)

- **A11Y-04.** The TeX source is the content description in both surfaces. The scroll surface sets it on the block. The paged surface draws on a canvas, so `PagedMathSemantics` (next to `PagedNoteSemantics`) emits one empty node per visible math box, with the source, or the failure text plus the source when the formula failed. This replaces the earlier statement that the paged surface has no semantics tree. TalkBack on a device was checked by the owner on 2026-11-01 with no problems.
- **Limits.** Source length, nesting depth and unbraced operand chains (`\frac\frac\frac...`, `x^x^x^...`) are validated before the library sees the text. Each call is timed: a call that took longer than 2000 ms (`MathLimits.CALL_BUDGET_MILLIS`) is reported as `MathFailure.TIMEOUT` and the formula shows as its source. The budget does not interrupt a running call (the library measure cannot be cancelled), so one runaway formula still holds the single measuring thread until it returns and later formulas wait behind it. The validator is the real defense; the budget only keeps a slow result from being used.
- **Lazy engine.** `rememberMathEngineOrNull(enabled)` builds the engine only for a document that has a display formula. Documents without math never load the library or its fonts.
- **Failure box.** The TeX source wraps whole and is never cut in the scroll surface, whose box keeps the placeholder height as a minimum. In the paged surface a source taller than the box ends with an ellipsis, and the full source stays in the semantics node. The error mark is `BifolioIcons.CircleAlert` (path data checked against Lucide circle-alert).
- **Bitmap cache.** The 16 MiB `LruCache` raises its cap to twice a bitmap that would not fit, so a very large window never drops a bitmap as it lands.
- **Fonts and licenses.** The KaTeX fonts are SIL OFL-1.1. Text and copyright holders (Design Science, Khan Academy) are in `core/designsystem/src/main/assets/fonts/OFL-katex.txt` and `NOTICE`. The in-app licenses screen (About, UI-11) lists them.
- **Classpath.** `:app:dependencies --configuration releaseRuntimeClasspath`: every `org.jetbrains.compose.*` module resolves to the matching `androidx.compose` 1.12.1 module (constraint "prevents symbols duplication"), so there are no duplicate classes; `assembleDebug` and `assembleRelease` succeed. No Material icons artifact is present. The merged release manifest has no `INTERNET` permission.
- **Size (PRF-06).** Release APK, unsigned, universal, no shrinking: 9,722,176 B at 4c8f5dd, 12,282,768 B with math, a delta of +2,560,592 B (2.44 MiB): fonts 1.77 MB compressed, KaTeX assets 0.31 MB, the rest dex. Native libraries are 15 to 20 KB per ABI, so a per-ABI split is about 53 KB smaller than the universal APK. R8 is not configured in the build, so the baseline already exceeds the 8 MB of PRF-06 before math, and the R8 delta cannot be measured until minification is set up. This is an open item for the owner.
- **Device checklist.** Release with R8 was run on 2026-10-22 (`docs/perf/M6.md`). The rest (warm-up and real timings, measurer thread safety, TalkBack, unbounded-width composition in the paged surface) was accepted by the owner on 2026-11-01 with no problems found; no numbers were recorded.

## Consequences

- Rendering failures degrade to monospace TeX source, never a crash.
- Rendered formulas are cached per (TeX, size, color).
- The renderer can be swapped without touching the paginator.

## Alternatives considered

- MathJax or KaTeX in a WebView: excluded by ADR-0002.
- JLatexMath: excluded by ADR-0001.
