# Fonts

Closes OPN-03. Fill one row per bundled font after checking the license file in its upstream repository. Do not bundle a font until its row is complete.

| Family | Role | Upstream repository | License (verified) | Version | Subset | Reserved font name notes |
|---|---|---|---|---|---|---|
| Literata | Body default | | | | Latin Ext, Greek, Cyrillic | |
| Source Serif 4 | Body | | | | Latin Ext, Greek, Cyrillic | |
| Atkinson Hyperlegible Next | Body accessible | | | | Latin Ext | |
| JetBrains Mono | Code | | | | Latin Ext, Greek, Cyrillic | |

Rules: OFL-1.1 only (ADR-0001). If a font declares a Reserved Font Name and we subset or modify it, rename the family in the font metadata as the license requires. Ship every license text in the app's licenses screen.
