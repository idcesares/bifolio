# CLAUDE.md

Instructions for Claude Code sessions working on Bifolio. Read this file fully before any task.

## What this project is

Bifolio is a read-only Markdown reader for Android that typesets documents like a book and turns foldables into two-page spreads. Package `dev.dcesares.bifolio`. License Apache-2.0. Owner: Isaac D'Césares (bifolio@dcesares.dev).

**Source of truth:** `SPEC.md`. Every requirement has an ID (`LAY-07`, `PAG-03`...). Decisions live in `docs/adr/`. Design tokens and motion live in `docs/design/DESIGN.md`. Milestones and their definitions of done live in `ROADMAP.md`. Testing strategy and the device matrix live in `docs/TESTING.md`.

If the spec is ambiguous or wrong, stop and propose a spec edit. Do not invent behavior silently.

## Hard rules (never break these)

1. **Clean room.** This is an independent implementation. Never read, fetch, paste or adapt code, strings, resources or assets from Pilcrow, PilcrowMD, Folio, Markwon, or any GPL or LGPL project. Do not search GitHub for "how Pilcrow does X". Derive everything from `SPEC.md`, official Android documentation and the libraries approved in the ADRs. See `PROVENANCE.md`.
2. **No network.** Never add `android.permission.INTERNET` or any network library to the app. No analytics, crash SDKs, ads or Play Services (PRV-01, PRV-02).
3. **No WebView.** No `android.webkit` usage anywhere (PRV-03, ADR-0002).
4. **Read-only.** The app never writes to user files. It only reads granted URIs.
5. **Licenses.** Only add dependencies whose license is on the allow list in ADR-0001. Record every new dependency in an ADR or in the dependency table of the relevant ADR.
6. **Strings in resources.** No user-facing string literals in Kotlin (UI-13).
7. **Layout from window, not device.** Never branch on device model or "is tablet" (LAY-01).
8. **Position is an anchor.** Never persist or restore reading position as a page number (ANC-01 to ANC-07).
9. **Icons are Lucide only** (UI-12). Do not add Material icons.
10. **Do not weaken tests to make them pass.** If a test is wrong, fix the test in a separate, explained commit.

## Module map

```
:app                    Application, activity, navigation, DI wiring, manifest
:core:model             Immutable document model: Block types, Anchor, Slice, Page, Geometry (pure Kotlin)
:core:parse             commonmark-java -> core:model (pure Kotlin/JVM)
:core:layout            Measure, column and gutter math, paginator, layout cache (pure Kotlin + text measuring interface)
:core:text              Android/Compose implementation of the text measuring interface used by core:layout
:core:highlight         Code highlighter adapter (ADR-0007)
:core:math              Math renderer adapter (ADR-0006)
:core:designsystem      Themes, typography, fonts, Lucide icons, motion tokens
:core:data              DataStore preferences, recents, anchors, folder grants
:feature:library        Library screen
:feature:reader         Reader surface, chrome, TOC, search, footnotes, posture handling
:feature:settings       Appearance sheet, settings, about, licenses
:benchmark              Macrobenchmark tests
:baselineprofile        Baseline and startup profile generator
```

Dependency direction: `feature` -> `core`. `core:layout` depends on `core:model` and an interface for text measurement, never on Compose UI, so geometry and pagination stay testable on the JVM.

## Commands

These commands are established in milestone M0. Keep this section in sync with the build.

| Purpose | Command |
|---|---|
| Fast unit tests for the engine | `./gradlew :core:model:test :core:parse:test :core:layout:test` |
| All unit tests | `./gradlew testDebugUnitTest` |
| Screenshot verify | `./gradlew verifyRoborazziDebug` |
| Screenshot record (only when a visual change is intended) | `./gradlew recordRoborazziDebug` |
| Static checks | `./gradlew ktlintCheck detekt lintDebug` |
| Policy checks (no INTERNET, no WebView, license allow list) | `./gradlew checkPolicy` |
| Debug build | `./gradlew :app:assembleDebug` |
| Foldable emulator tests | `./gradlew pixelFoldApi37DebugAndroidTest` |
| Benchmarks (device attached) | `./gradlew :benchmark:connectedBenchmarkAndroidTest` |
| Baseline profile | `./gradlew :app:generateBaselineProfile` |

## How to work

1. **Start from an issue** that cites spec IDs and acceptance criteria. If there is no issue, write the plan first.
2. **Plan before editing** for anything touching more than one module. List the spec IDs, the files, the tests you will add.
3. **Test first for `core:*`.** Write the failing test that encodes the spec rule, then the code. Geometry and pagination rules get property-based tests.
4. **Screenshot tests for UI.** After UI changes run `verifyRoborazziDebug`. If a diff is intended, record, then open the changed PNGs and look at them before committing. Describe what changed in the PR.
5. **Small commits**, conventional style: `feat(layout): gutter tracks separating fold (LAY-12)`.
6. **Ask the reviewer subagent** to review the diff against `SPEC.md` before opening a PR.
7. **PR description** lists every spec ID touched and how each was verified (QA-05).

## Subagents (in `.claude/agents/`)

- `engine`: core:model, core:parse, core:layout, core:text. Test-first, JVM-fast.
- `ui-motion`: designsystem and features. Works from DESIGN.md and screenshot tests.
- `perf-a11y`: baselines, benchmarks, accessibility, reduce motion.
- `reviewer`: independent review of a diff against the spec, the hard rules and test quality. Read-only.

Run `engine` and `ui-motion` in separate worktrees only when their files do not overlap.

## Style

- Kotlin official style, enforced by ktlint. Explicit API mode in `core:*` modules.
- Immutable data in `core:model`. No Android types in `core:model`, `core:parse`, `core:layout`.
- Coroutines: parsing and layout on `Dispatchers.Default`; never block the main thread.
- No reflection-based libraries; keep R8 full mode clean.
- Names: describe behavior (`SpreadCalculator`, `AnchorMapper`), avoid vague names (`Manager`, `Helper`, `Utils`).

## Hooks

`.claude/settings.json` installs:

- `guard.sh` (before edits): blocks writes that add network permissions, WebView, tracking SDKs or forbidden project names to source files. Files under `build-logic/` and `config/` are exempt, because the `checkPolicy` task and lint configuration have to name what they forbid. Put policy code only there.
- `format.sh` (after edits): formats edited Kotlin files with ktlint if it is installed.
- `verify.sh` (before stopping): runs the fast engine tests when Kotlin files changed and the Gradle wrapper exists. A failure means the task is not done.

Hooks need `bash` and `jq`.
