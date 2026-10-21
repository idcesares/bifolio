# ADR-0006 update: math renderer spike result (OPN-02)

Status: Proposed (draft, awaiting owner approval; no dependency is added until approved)
Date: 2026-10-21
Spec IDs: OPN-02, SYN-12, PAG-07, A11Y-04
Updates: ADR-0006 (math rendering behind an adapter)

## Context

ADR-0006 names huarangmeng/latex as the first implementation and RaTeX as the fallback, pending OPN-02. The spike ran in a throwaway Gradle project outside the repo (nothing in the repo build files changed). It mirrored the repo toolchain: AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Robolectric 4.17 (sdk 35, native graphics, `w412dp-h915dp-xhdpi`), minSdk 26, JDK 21.

Scope limit: only checks that run on the JVM or Robolectric were executed. Device-only checks are listed below.

## Recommendation

Adopt **huarangmeng/latex 1.6.1** with the KaTeX font artifact, behind the `:core:math` adapter, **conditionally**: the adapter guards in "Mandatory adapter guards" are part of the decision, and the device checklist must pass in M5 before the dependency is considered settled.

The bar for "conditional": 18 of 20 corpus formulas render correctly, no crash on any failure input once guarded, and sizing is exact. That meets OPN-02 only if the owner accepts the known rendering defects (see "Risks accepted"), above all the wrong `cases` output, which is common in real documents and cannot be fixed cleanly in the adapter. If the owner rejects that defect, the alternative is to run the same 20 formulas against RaTeX on a device (KaTeX-compatible core, no `cases` defect known but untested) before approving either library. Keep **RaTeX** as the documented fallback. RaTeX was desk-checked only, because its native library cannot run on the JVM test host.

Exact coordinates (Maven Central, resolve to the `-android` variants through Gradle module metadata):

| Artifact | Version | License |
|---|---|---|
| `io.github.huarangmeng:latex-renderer` | 1.6.1 | MIT |
| `io.github.huarangmeng:latex-fonts-katex` | 1.6.1 | MIT (code), SIL OFL-1.1 (KaTeX font files) |

Fallback: `io.github.erweixin:ratex-android:0.1.15` (MIT, fonts OFL-1.1).

## Comparison

| Criterion | huarangmeng/latex 1.6.1 | RaTeX 0.1.15 (desk check) |
|---|---|---|
| License | MIT (LICENSE and POMs). KaTeX fonts are OFL-1.1 by font metadata; the project's third-party notice mislabels them as MIT and the AAR ships no license file. Allowed (fonts only, ADR-0001) | MIT plus a note that bundled KaTeX fonts are OFL-1.1. GitHub shows "NOASSERTION". Both allowed |
| Maturity | Created 2026-01-02, 131 stars, 1 open issue, 6 contributors but one owner with 173 commits (bus factor 1), 30 releases, latest 2026-10-19. API changed a lot between 1.0 and 1.6 | Created 2026-03-10, 1540 stars, 17 contributors, 13 open issues, latest 2026-10-16, 0.1.x |
| Android support | AAR minSdk 23, Compose. Standard variant built with Kotlin 2.3.10 and Compose Multiplatform 1.10.3; ran fine with Kotlin 2.4.21 and Compose 1.12.1 | minSdk 21, Canvas view, no Compose dependency for layout data |
| Reflection | None claimed. Class scan found no reflection, no class loading by name. Consumer rules are only `-keepattributes` and `-dontwarn com.hrm.latex.**` | Native code through JNI, no reflection in the Kotlin wrapper expected (not verified) |
| Network | None. Manifest has no permissions. Scan found no URL connection or intent use. Writes a temp file in the app cache dir (glyph bounds, font loading), not user files | Not verified beyond manifest and POM (no network dependency) |
| Transitive deps new to the app | `components-resources-android` 1.10.3, `ui-backhandler-android` 1.9.1, `kotlinx-serialization-core-jvm` 1.7.3, `org.jetbrains:annotations`. All Apache-2.0 (POMs). Material3 and graphics-path already in the app | kotlin-stdlib 1.9.24, androidx.annotation, kotlinx-serialization-json 1.6.3, coroutines 1.7.3 (Apache-2.0) |
| Size (R8 full mode, release, unsigned) | Baseline app 704,458 B. Renderer with system fonts +175 KB. Renderer with KaTeX fonts 1,191,581 B, **+487 KB** (fonts about 310 KB compressed) | AAR 4.1 MB. `libratex_ffi.so` per ABI: arm64 3.2 MB, armv7 2.3 MB, x86_64 3.6 MB uncompressed. Expect roughly +2 to +3 MB per ABI split (estimate, not built) |
| 16 KB page size | Pure JVM, not applicable | arm64 and x86_64 LOAD alignment 16384 with RELRO aligned (OK). armv7 is 4-byte aligned, not subject to the rule |
| JVM and Robolectric testable | Yes, ran 20 formulas plus failure set | No (native), device only |
| Size before drawing | Yes: `measure()` returns width, height, baseline in px; measured equals drawn within 1 px for 20 of 20 formulas | Yes: `parseBlocking` returns a pure-data `DisplayList(width, height, depth, items)`. Cleaner for `core:layout` |
| Failure semantics | `measure()` returns null for empty, blank, unbalanced begin, stray end, stray brace. Several malformed inputs return a size with no diagnostic. See evidence | Not tested |
| Public API friction | Measurer and exporter are only creatable through composables; font loader is internal | Plain blocking calls |

## Evidence (huarangmeng/latex, JVM and Robolectric)

1. **Rendering quality.** All 20 formulas render without a crash. All 20 PNGs were inspected by eye (Robolectric, KaTeX fonts). Tally: **18 of 20 correct**, **1 materially wrong (F13)**, **1 with a layout deviation (F17)**. Minus rendered as a hyphen (see below) is a cosmetic defect that touches F9, F11, F13, F15 and F17 on top of that tally.
   - F1 to F12, F14, F16, F18 to F20: correct. Covered fractions, roots, sub/superscripts, big operators with limits, `\left...\right` scaling, `pmatrix`, `vmatrix`, `\hat`, `\vec`, `\mathbb`, `\text` in a subscript.
   - **F13 (`cases`) is wrong.** `f(x)=\begin{cases} x^2 & x\ge 0 \\ -x & x<0 \end{cases}` draws `x >= 0  if  x^2` over `x < 0  if  -x`: the two columns are swapped and an italic "if" is injected between them. A second probe (`\begin{cases} 1 & \text{if } x>0 \\ 0 & \text{otherwise} \end{cases}`) draws `if x>0  if  1` and `otherwise  if  0`, so the defect is systematic for `cases` (the library appears to treat the cell after `&` as the condition and reorder), not specific to one formula.
   - **F17 (`aligned`) deviates.** The `=` signs align, but the left column is centered instead of right-aligned (second row starts further right than the first). Readable, not exact.
   - **Minus drawn as a hyphen.** Unary and binary minus (`-x`, `b^2-4ac`, `1-p^{-s}`, `x=-1+2-3`) use a hyphen-width glyph instead of U+2212. Readable but typographically off in a book-quality reader.
2. **Measure equals draw.** Measured size equals drawn size within 1 px for all 20 formulas (0 mismatches). Sizing atomic display blocks for PAG-07 from `measure()` is sound.
3. **Font race.** Widths with system fonts and with KaTeX fonts differ for 20 of 20 formulas (F11: 288 px against 311 px). Measuring before fonts are in would make pagination unstable. The library auto-loads KaTeX fonts asynchronously with no public ready signal, and under Robolectric the load never completed. The deterministic route is the public `LatexFontFamilies` constructor fed with the KaTeX TTFs read from the AAR assets; this worked.
4. **Cost.** JVM, KaTeX injected: cold measure about 0.6 ms median per formula, 2.3 ms max, 13.5 ms for all 20; cached calls at most 0.2 ms. The first call of a process paid 550 ms of warm-up in one run. Android numbers are device-pending.
5. **Dark theme.** `LatexTheme.dark(fg, Color.Transparent)` produced exact foreground pixels over a transparent box. Hazard: author `\textcolor{#000000}{...}` forces black, invisible on a dark theme.
6. **200% font scale.** Density fontScale 2.0 widens formulas by 1.67 to 1.82 (padding is fixed). F19 (display) goes from 574 px to 1045 px against a 696 px column (fit scale 0.70). A composed `Latex()` under a width constraint **clips** (drew 824 px of 1045 px), it does not shrink. The host must render unbounded and scale.
7. **Failures (stand-ins for C-15, appendix).** No input crashed the test process, but behavior needs guards:
   - 10,000 nested braces: parse, measure and export throw `StackOverflowError`. 500-deep superscripts: measure and export throw `StackOverflowError`.
   - `\rule{100000em}{100000em}` measures 3,600,010 x 3,600,007 px and export throws `OutOfMemoryError`. `\hspace{99999999pt}` measures 2e8 px wide and export throws `OutOfMemoryError`. A 5000-term sum measures 579,604 px wide.
   - Recursive and exponential macros return quickly with `MACRO_ERROR` diagnostics (the exponential one yields 10,001 errors).
   - Diagnostics are sparse. Present: stray end (warning), stray brace (error), recursive macro (error). Absent: unknown commands, unbalanced begin, mismatched environment, `\frac{a}` with empty denominator, dangling `^`, open `\left`.
   - Unknown commands become `Command` nodes drawn in the error color. In the sampled formulas `Command` nodes appear only for unknown commands, so they are a usable failure signal.
8. **Accessibility.** With accessibility enabled the library exposes a MathSpeak description ("fraction: 1 over 2"). A11Y-04 asks for TeX source, so the adapter must replace the semantics.
9. **Links.** `\href{url}{text}` yields a hyperlink node with a click callback; the library has no URL opener. Behavior must be decided by the app.
10. **Policy scan.** No INTERNET-type permission in the manifest, no WebView use, no reflection, no class loading by name (class-file byte scan of renderer, parser and base AARs).

## Mandatory adapter guards (part of the decision)

1. Inject KaTeX fonts through `LatexFontFamilies` before the first measurement. Never rely on auto-load or system fonts.
2. Pre-validate the TeX in the adapter: balanced braces, matched `\begin`/`\end`, maximum nesting depth, maximum length. Reject before calling the library.
3. Treat `measure() == null`, library error diagnostics and any `Command` node as failure. Fall back to monospace TeX source with the subtle error mark (SYN-12).
4. Catch `Throwable` around parse, measure and render (covers `StackOverflowError` and `OutOfMemoryError`).
5. Apply size sanity bounds on the measured result (for example reject width or height beyond a fixed multiple of the column), so absurd `\rule` and `\hspace` values never reach layout or a bitmap.
6. Render with unbounded width and apply the PAG-07 scale in the host.
7. Override semantics with the TeX source (A11Y-04).
8. Serialize measurer access on `Dispatchers.Default` (thread safety unverified, see below).
9. Cache per (TeX, size, color, fontScale, display or inline).
10. Disable or ignore the hyperlink callback unless the owner decides otherwise (see ambiguity 5).
11. Ship attribution for the KaTeX fonts (OFL-1.1) in the licenses screen and NOTICE.

## Device-pending checks (not verifiable on the JVM)

- Rendering of an R8 full-mode release build (size was measured, runtime behavior of the minified build was not).
- Real Android text metrics, cold-start and warm-up cost of the first formula (550 ms seen on JVM once).
- Thread safety of the measurer off the main thread (it worked on a worker thread in Robolectric; treat as not safe until proven).
- KaTeX font auto-load through compose-resources on a device (not needed if guard 1 is kept).
- Cost of the glyph-bounds Typeface write to the cache dir (upstream issue #51).
- TalkBack behavior with the overridden semantics.
- Unbounded-width composition without clipping inside the paged surface.
- Inline (text style) against display (display style) fidelity.
- Everything about RaTeX rendering and its native library on ABIs.

## SPEC ambiguities (proposed wording, not applied)

1. **SYN-12 "parse failure".** Proposed: "A formula is a failure when the adapter's validation rejects it, the renderer reports an error diagnostic, the measurement is absent, the formula contains an unknown command, or rendering throws. On failure the TeX source is shown in monospace with a subtle error mark."
2. **A11Y-04.** Proposed: "Math exposes its TeX source as the content description. Spoken math (MathSpeak) is not used."
3. **PAG-07 at large font scale.** Proposed: "Display math is scaled down to the column width, but never below 60% of its natural size. Below that it scrolls horizontally inside its own block in scroll mode and is shown at the 60% floor, clipped with a visible edge fade, in paged mode." (Number to be confirmed by the owner.)
4. **Author color in dark themes.** Proposed: "Color commands in TeX are ignored; math always uses the theme foreground."
5. **`\href` in math.** Proposed: "Links inside math are not interactive; the link text renders as plain math."
6. **Corpus files C-14 and C-15 do not exist.** `docs/TESTING.md` and `ROADMAP.md` cite `testdata/corpus/math-inline-display.md` and `math-errors.md`, but neither is in the repo. Proposed: create them from the appendix sets below (20 formulas in inline and display forms; failure set) before the M5 adapter work.
7. **Inline against display style.** Proposed: "`$...$` renders in text style, `$$...$$` and fenced `math` in display style."
8. **Font license label.** Proposed NOTICE line: "KaTeX fonts, SIL Open Font License 1.1, bundled through latex-fonts-katex". Upstream labels them MIT; the font metadata says OFL-1.1.
9. **ADR-0001 transitive artifacts.** Proposed note: "JetBrains Compose Multiplatform runtime artifacts (components-resources, ui-backhandler) are Apache-2.0 and allowed as transitive dependencies of an approved library."

## Risks accepted

- **Rendering defects (owner decision needed).** (a) `cases` swaps columns and injects "if". Options: file an upstream issue and wait; preprocess `cases` in the adapter into an `array` with a left brace (`\left\{\begin{array}{ll} ... \end{array}\right.`), which is a TeX-level rewrite with its own risk; or accept the defect and document it. (b) `aligned` centers the left column. (c) Minus uses a hyphen glyph; the adapter could rewrite `-` to `\minus`-style or U+2212 in math mode, untested. The preprocessing options were not tried in the spike.
- Bus factor 1 and a fast-moving API on the primary candidate. Mitigation: the adapter interface isolates it, the version is pinned, and RaTeX is a documented fallback.
- Standard variant built with Kotlin 2.3.10 while the repo uses 2.4.21. It worked in the spike; the guard is the M5 device run.

## Appendix A: the 20 formulas used (stand-in for C-14)

```
E = mc^2
a^2 + b^2 = c^2
\frac{a}{b}
x_{i,j}^{2}
\sqrt{x^2+y^2}
\sqrt[3]{\frac{a}{b}}
\alpha + \beta = \gamma
\sum_{i=1}^{n} i = \frac{n(n+1)}{2}
\int_0^\infty e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}
\lim_{x\to 0}\frac{\sin x}{x}=1
x = \frac{-b \pm \sqrt{b^2-4ac}}{2a}
\begin{pmatrix} a & b \\ c & d \end{pmatrix}
f(x)=\begin{cases} x^2 & x\ge 0 \\ -x & x<0 \end{cases}
\hat{H}\psi = i\hbar\frac{\partial}{\partial t}\psi
\prod_{p\ \text{prime}} \frac{1}{1-p^{-s}} = \sum_{n=1}^\infty \frac{1}{n^s}
\nabla\times\vec{B} = \mu_0\vec{J}+\mu_0\varepsilon_0\frac{\partial\vec{E}}{\partial t}
\begin{aligned} (a+b)^2 &= a^2+2ab+b^2 \\ (a-b)^2 &= a^2-2ab+b^2 \end{aligned}
\mathbb{R}\subset\mathbb{C},\quad \forall \epsilon>0\ \exists\delta>0
\left(\sum_{k=1}^{n} a_k b_k\right)^2 \le \left(\sum_{k=1}^{n} a_k^2\right)\left(\sum_{k=1}^{n} b_k^2\right)
\det\begin{vmatrix} a_{11} & a_{12} & a_{13}\\ a_{21} & a_{22} & a_{23}\\ a_{31} & a_{32} & a_{33}\end{vmatrix}
```

## Appendix B: failure set used (stand-in for C-15)

```
empty:                (empty string)
blank:                three spaces
lone-frac:            \frac{a}
unknown-command:      \unknowncmd{x} + 1
unbalanced-begin:     \begin{matrix} a & b
stray-end:            \end{pmatrix}
stray-brace:          }{
dangling-script:      x^
open-left:            \left( x
double-dollar:        $$
mismatched-env:       \begin{pmatrix} a \end{bmatrix}
recursive-def:        \def\a{\a}\a
exponential-macro:    \newcommand{\a}{\a\a}\a
nested-braces-10k:    "{" x 10000, then x, then "}" x 10000
deep-superscript-500: x followed by "^{x" x 500, then "}" x 500
huge-rule:            \rule{100000em}{100000em}
huge-hspace:          a\hspace{99999999pt}b
long-sum-5000:        x_{1}+x_{2}+ ... +x_{5000}
href-url:             \href{https://example.invalid/x}{link}
color-author:         \textcolor{red}{x} + \color{#00ff00} y
```

## Appendix C: reproducing

The spike project lives outside the repo (`C:\Users\idces\.claude\jobs\6e1fe991\tmp\math-spike`). It holds `Formulas.kt` (the sets above), `MathSpikeTest.kt` (ten Robolectric tests), the three size-comparison apps and the binary scan scripts. It is disposable and not part of the build.
