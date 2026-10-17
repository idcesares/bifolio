# Architecture Decision Records

Each ADR records one decision, its context and its consequences. ADRs are immutable once accepted; to change a decision, write a new ADR that supersedes the old one and update the status line of the old one.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-clean-room-and-license.md) | Clean-room rewrite, Apache-2.0, dependency license allow list | Accepted |
| [0002](0002-native-compose-no-webview.md) | Native Compose rendering, no WebView | Accepted |
| [0003](0003-parser-commonmark-java.md) | Parser: commonmark-java | Accepted |
| [0004](0004-own-paginator.md) | Own paginator over a text-measuring interface | Accepted |
| [0005](0005-anchor-position-model.md) | Reading position as an anchor | Accepted |
| [0006](0006-math-renderer-adapter.md) | Math rendering behind an adapter | Accepted, library pending OPN-02 |
| [0007](0007-code-highlighting-adapter.md) | Code highlighting behind an adapter | Accepted |
| [0008](0008-no-network-v1.md) | No network access in v1 | Accepted |
| [0009](0009-fonts-and-icons.md) | Bundled OFL fonts and Lucide icons | Accepted, licenses pending OPN-03, OPN-04 |
| [0010](0010-window-and-fold-detection.md) | Layout from window metrics and WindowManager folds | Accepted |
| [0011](0011-testing-strategy.md) | Testing strategy and verification ladder | Accepted |
| [0012](0012-android-runtime-dependencies.md) | Android runtime and UI test dependencies for M1 | Accepted |

Template for new ADRs:

```
# ADR-NNNN: Title

Status: Proposed | Accepted | Superseded by ADR-XXXX
Date: YYYY-MM-DD
Spec IDs: ...

## Context
## Decision
## Consequences
## Alternatives considered
```
