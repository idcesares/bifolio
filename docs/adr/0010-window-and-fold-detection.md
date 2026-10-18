# ADR-0010: Layout from window metrics and WindowManager folds

Status: Accepted
Date: 2026-10-15
Spec IDs: LAY-01 to LAY-03, LAY-10 to LAY-14, POS-01 to POS-06, ANC-07

## Decision

1. Geometry is computed from `WindowMetrics` (current window bounds), `WindowInsets`, density, font settings and the list of `FoldingFeature`s from `WindowInfoTracker.windowLayoutInfo(activity)`, collected with lifecycle awareness.
2. A fold participates in layout only if it intersects the window. Its bounds are converted to the reader's coordinate space.
3. Posture classification uses `state`, `orientation`, `isSeparating` and `occlusionType` exactly as defined in SPEC section 4.4.
4. Window size classes (including the large and extra-large classes) are used only for chrome decisions (pane versus sheet, rail versus bar). Column count comes from measure (LAY-07), never from size classes.
5. The app targets the latest API level, never locks orientation, and declares itself resizable. The Activity handles size and orientation configuration changes itself (ANC-07); recreation must still work.
6. A `GeometrySource` abstraction makes all of this injectable, so tests can supply fake folds (window-testing artifact) for every posture.

## Consequences

- Behavior is identical on any foldable, tri-fold, tablet or desktop window that reports the same geometry.
- Samsung Flex Mode, Pixel book posture and tabletop come from the same code path.

## Test-only dependencies (M3 instrumented suite)

| Artifact | License | Use |
|---|---|---|
| `androidx.test:runner` | Apache-2.0 | Instrumentation runner for `:app` androidTest |
| `androidx.test.ext:junit` | Apache-2.0 | `ActivityScenario` and AndroidX JUnit4 integration |
| `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest` | Apache-2.0 | Compose test rule and its debug manifest |
| `androidx.window:window-testing` | Apache-2.0 | Fake folds for postures (already in the catalog) |

Gradle Managed Device `pixelFoldApi37` (Pixel Fold profile, API 37, Google image) runs `AnchorSurvivalTest`:
`./gradlew pixelFoldApi37DebugAndroidTest`. The fold and unfold case drives real device states through
`cmd device_state` and is skipped on images without CLOSED and OPENED states. Process death is covered by the
cold reopen case, which restores from storage exactly as a restarted process does (ANC-07).
