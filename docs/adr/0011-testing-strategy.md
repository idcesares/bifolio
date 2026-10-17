# ADR-0011: Testing strategy and verification ladder

Status: Accepted
Date: 2026-10-15
Spec IDs: QA-01 to QA-05, PRF-01 to PRF-07

## Decision

Verification is layered so that agents and humans get fast, objective feedback. Details and the device matrix live in `docs/TESTING.md`.

1. **JVM unit and property tests** for `core:model`, `core:parse`, `core:layout` (seconds).
2. **Screenshot tests** with Roborazzi on Robolectric across the device matrix, including fake folds (minutes, headless).
3. **Instrumented tests** on Gradle Managed Devices with foldable emulator profiles for fold, unfold and posture transitions (CI).
4. **Macrobenchmarks** on a real device for performance budgets.
5. **Owner review on the foldable** for motion and typography at milestones M1, M4 and M6.

## Dependencies

Test-only dependencies introduced by this ADR. All licenses are on the ADR-0001 allow list.

| Library | Version | License | Use |
|---|---|---|---|
| kotest-property | 6.2.6 | Apache-2.0 | Property-based tests for geometry, pagination and anchors (QA-01), on the JUnit platform |

jqwik was considered and rejected: EPL-2.0 would need its own ADR (ADR-0001) and a separate test engine.

## Consequences

- Most logic must be reachable from JVM tests; this shapes module boundaries (ADR-0004).
- Screenshot baselines are part of the repository and change only with an explained, intended visual change.
