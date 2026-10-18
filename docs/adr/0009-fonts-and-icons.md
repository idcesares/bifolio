# ADR-0009: Bundled OFL fonts and Lucide icons

Status: Accepted (fonts confirmed in docs/design/FONTS.md, OPN-03; icons pending OPN-04)
Date: 2026-10-15
Spec IDs: TYP-01, UI-12

## Decision

**Fonts.** Bundle subset font files, preferably variable fonts, licensed SIL OFL-1.1:

| Role | Family | Notes |
|---|---|---|
| Body default | Literata | Designed for long-form screen reading |
| Body alternative | Source Serif 4 | Optical sizes |
| Body accessible | Atkinson Hyperlegible Next | Low-vision legibility |
| Code | JetBrains Mono | Clear distinctions between similar glyphs |
| UI chrome | Same as body family, or the system sans for chrome only | Decided in DESIGN.md |

Subsets cover Latin Extended, Greek and Cyrillic. Each font's license file ships in the app and is listed in the licenses screen. OPN-03 confirms each license from the upstream repository and records it in `docs/design/FONTS.md`. No downloadable fonts in v1.

**Icons.** Lucide (ISC license), outlined, 1.5dp stroke at 24dp, imported as vector drawables or through a Compose library whose license is on the allow list. OPN-04 confirms the source. No Material icon artifacts are added.

## Consequences

- APK size includes fonts; subsetting keeps them within PRF-06.
- Visual identity is consistent: one icon family, one stroke weight.
