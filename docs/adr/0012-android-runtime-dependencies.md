# ADR-0012: Android runtime and UI test dependencies for M1

Status: Accepted
Date: 2026-10-16
Spec IDs: DOC-02, DOC-05, UI-01, LAY-03, PRV-01, PRV-02, QA-02, QA-04

## Context

M1 is the first milestone with an Activity, a Compose UI, persisted state and screenshot tests. Every library below was checked against the ADR-0001 allow list and against PRV-01 and PRV-02 (no network permission, no Play Services, no analytics). `checkPolicy` scans the resolved shipped classpaths and keeps enforcing both.

## Decision

Shipped dependencies (all Apache-2.0):

| Library | Version | License | Reason |
|---|---|---|---|
| `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 | `ComponentActivity`, `enableEdgeToEdge`, result launchers for `ACTION_OPEN_DOCUMENT`, `BackHandler` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Apache-2.0 | ViewModels that survive configuration changes and hold the loaded `Document` |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | Apache-2.0 | Lifecycle-aware state collection (`collectAsStateWithLifecycle`) |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Apache-2.0 | Recents, anchors and grants in `core:data`, no Room, no SharedPreferences |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 | `Dispatchers.Default` for parse and layout, `Dispatchers.Main` bridge |
| `androidx.compose:compose-bom` and `ui`, `ui-graphics`, `foundation`, `ui-tooling-preview` | BOM 2026.09.00 | Apache-2.0 | Reading surface and library UI. Material 3 is not used in M1 |

Debug-only and test-only dependencies (never shipped in a release build):

| Library | Version | License | Reason |
|---|---|---|---|
| `androidx.compose.ui:ui-tooling` (debug) | BOM | Apache-2.0 | `@Preview` rendering in Android Studio |
| `androidx.compose.ui:ui-test-manifest` (debug) | BOM | Apache-2.0 | Activity host for Compose tests |
| `androidx.compose.ui:ui-test-junit4` | BOM | Apache-2.0 | `createComposeRule` in screenshot tests |
| `io.github.takahirom.roborazzi:roborazzi-compose`, `roborazzi-junit-rule` | 1.76.0 | Apache-2.0 | Screenshot capture and verification (ADR-0011) |
| `org.robolectric:robolectric` | 4.17 | MIT | Android runtime on the JVM |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Apache-2.0 | Deterministic coroutine tests |
| `junit:junit` | 4.13.2 | EPL-1.0 | Robolectric and the Compose test rule are JUnit 4 based. Test scope only |
| `org.junit.vintage:junit-vintage-engine` | 6.1.3 | EPL-2.0 | Runs JUnit 4 tests on the JUnit Platform next to the existing Jupiter tests. Test scope only |

Navigation is a saveable screen state held by the Activity plus `BackHandler`; `navigation-compose` is not added. Two screens do not justify a graph library.

## Consequences

- The two EPL entries are test-scope, unmodified and not distributed in the APK, which is the same position as `junit-jupiter` (EPL-2.0) since ADR-0001. `checkPolicy` does not scan test classpaths, so these are recorded here by hand.
- `androidx.core` and `androidx.annotation` arrive transitively and are Apache-2.0.
- Material 3, Material icons and `androidx.window` stay out of M1 code. `material3` remains in the catalog for the chrome work in M4.

## Alternatives considered

- **Navigation Compose.** Rejected for M1, see above.
- **Room.** Rejected: recents, anchors and grants are small key-value data.
- **kotlinx.serialization for recents.** Rejected: a small hand-written codec avoids a compiler plugin and a dependency.
