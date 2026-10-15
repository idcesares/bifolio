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
