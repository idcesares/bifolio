# Provenance

Bifolio is an independent implementation written from its own specification (`SPEC.md`).

## Prior art

Before Bifolio, the author experimented with a private prototype ("Folio") that was a fork of **PilcrowMD** (github.com/pilcrowmd/pilcrow, GPL-3.0-or-later, by pleree). That prototype was never published and is not part of this repository.

Bifolio reuses **no code, text, resources, assets or version history** from PilcrowMD or from the prototype.

## Process

1. The product was specified in prose (`SPEC.md`, `docs/design/DESIGN.md`, `docs/adr/`) describing behavior, rules and formulas, without code excerpts. The specification draws on public Android documentation, typography literature and the author's own product decisions.
2. This repository was created fresh, with no shared history.
3. Implementation is done in sessions that do not have access to the prototype or PilcrowMD source. Contributors and coding agents are instructed not to consult them (`CLAUDE.md`, hard rule 1), and repository hooks reject source changes that reference them.
4. Dependencies are limited to permissively licensed libraries (ADR-0001). Libraries used by the prototype that are GPL-licensed or unmaintained are not used.

We acknowledge PilcrowMD as prior art in the space of native Android Markdown readers. This acknowledgment is a courtesy and documents independence; it does not indicate derivation.
