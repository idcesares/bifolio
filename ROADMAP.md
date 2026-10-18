# Roadmap to v1.0

SCO and OUT IDs are summaries; they are satisfied by the detailed IDs below. Each milestone closes only when every item in its "Done when" column is objectively true. Track each milestone as a GitHub milestone; each row of its scope becomes an issue citing spec IDs.

| # | Milestone | Scope (spec IDs) | Done when | Owner checkpoint |
|---|---|---|---|---|
| M0 | Foundation | OPN-01, OPN-06, QA-01 to QA-05, PRV-01 to PRV-03 | Multi-module project builds; CI runs unit tests, ktlint, detekt, lint, `checkPolicy`; adding an INTERNET permission or a WebView import makes CI fail (proven by a test PR); versions pinned in the version catalog; CLAUDE.md commands all work | Isaac approves module layout |
| M1 | Read a document | DOC-01 to DOC-09, SYN-01 to SYN-07, SYN-15, LAY-03, SCO-03 (scroll), UI-01, UI-13, UI-15, UI-16 | Corpus C-01 to C-13, C-20, C-23 to C-27 render in scroll mode; open from picker, view intent and share intent works; recents persist; screenshot baselines approved | Isaac reads three real documents on the fold |
| M2 | Paginator | LAY-04 to LAY-09, PAG-01 to PAG-12, OPN-05 | Property tests for PAG-02, PAG-03, PAG-11 and character coverage pass; paged mode works for C-01 to C-13 and C-22; PRF-04 measured on the emulator | none |
| M3 | Foldable spreads and continuity | LAY-01, LAY-02, LAY-10 to LAY-14, POS-01 to POS-06, ANC-01 to ANC-07, NAV-01 to NAV-06 | Device matrix DM-01 to DM-12 screenshots approved; GMD tests prove the anchor line stays visible across fold, unfold, posture change, rotation and process death | Isaac folds and unfolds 20 times on his device and never loses his place |
| M4 | Design and motion | TYP-01 to TYP-10, UI-02 to UI-12, MOT-01 to MOT-09, OPN-03, OPN-04 | Tokens frozen in DESIGN.md; fonts and icons licensed and recorded; all four themes pass contrast checks in screenshot tests; reduced-motion path tested; app icon final | Isaac approves motion and typography on the device |
| M5 | Rich content | SYN-08 to SYN-14, NAV-07 to NAV-09, UI-07 to UI-09, UI-14, OPN-02 | Corpus C-14 to C-19 and C-21 paginate per spec; math spike result recorded in ADR-0006; TOC, search and footnotes work in both modes | none |
| M6 | Performance and accessibility | PRF-01 to PRF-07, A11Y-01 to A11Y-06 | Every PRF budget measured and passing on the reference phone and the foldable; TalkBack continuous reading works; 200% font scale screenshots pass; baseline profile shipped | Isaac uses the app for a week of real reading |
| M7 | Release | PRV-04 to PRV-06, UI-11, SCO-11 | Signed with the upload key; Play listing with foldable screenshots and the unfold video; Data safety form "no data collected"; privacy policy published; closed test completed; repository made public with README, LICENSE, NOTICE, PROVENANCE | Isaac presses publish |

## After v1.0 (not scheduled)

- v1.1: opt-in remote images (new ADR), bookmarks, pt-BR translation.
- v1.2: tabletop control deck refinements, reading statistics.
- v2: offline Mermaid rendering, multilingual UI, library folders.
