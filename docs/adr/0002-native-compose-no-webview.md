# ADR-0002: Native Compose rendering, no WebView

Status: Accepted
Date: 2026-10-15
Spec IDs: OUT-11, PRV-03, PRF-01, PRF-02

## Context

Markdown readers commonly render HTML in a WebView. CSS multi-column layout would give pagination, hyphenation and justification cheaply. However, a WebView adds process and memory cost, slows cold start, makes theming, TalkBack and selection behave like a web page, and makes exact placement of a gutter on a physical hinge depend on injected geometry.

## Decision

All document rendering is native: Kotlin, Jetpack Compose, and Android text layout. No `android.webkit` class is referenced by the app.

## Consequences

- Bifolio owns pagination (ADR-0004), which is the main engineering investment.
- Theming, motion, accessibility semantics and fold-aware geometry are fully under our control.
- Mermaid diagrams are not rendered in v1 (OUT-04).

## Alternatives considered

- **WebView with CSS columns.** Rejected for the reasons above.
- **Markwon with RecyclerView.** Rejected: unmaintained since 2021, does not paginate, View-based.
- **Off-the-shelf Compose Markdown renderers** (for example mikepenz/multiplatform-markdown-renderer). Good for scrolling, but none paginate. May be studied for API design only if its license is on the allow list.
