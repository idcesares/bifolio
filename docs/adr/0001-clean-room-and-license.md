# ADR-0001: Clean-room rewrite, Apache-2.0, dependency license allow list

Status: Accepted
Date: 2026-10-15
Spec IDs: PRV-02, QA-04

## Context

A prototype called Folio existed as a fork of PilcrowMD, which is licensed GPL-3.0-or-later. A fork must remain GPL and carry upstream notices. Bifolio is a new product with a different scope (read-only, foldable-first) and the owner wants to choose its license freely.

## Decision

1. Bifolio is an **independent implementation written from `SPEC.md`**. No code, strings, resources, assets or repository history from the prototype or from PilcrowMD are used. The process is recorded in `PROVENANCE.md`.
2. Bifolio is licensed under **Apache-2.0** (explicit patent grant, compatible with F-Droid and Google Play).
3. Dependencies must use a license from this **allow list**: Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC, Unicode-3.0 / Unicode-DFS, SIL OFL-1.1 (fonts only), EPL-2.0 only for unmodified libraries and only with a dedicated ADR. Forbidden: GPL, LGPL, AGPL, SSPL, any "non-commercial" or source-available license.
4. CI checks every resolved dependency against the allow list (`checkPolicy`).

## Consequences

- Libraries used by the prototype that are GPL or unmaintained (JLatexMath, Markwon, Prism4j) are excluded.
- Implementers must not look at PilcrowMD or prototype source while working on Bifolio.
- The open source licenses screen is generated from dependency metadata.

## Alternatives considered

- **Keep the fork under GPL-3.0.** Rejected: carries upstream identity and obligations into a product with a different scope, and limits licensing choices.
- **MIT.** Acceptable, but lacks the explicit patent grant of Apache-2.0.
