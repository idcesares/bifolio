# Release checklist

The first distribution is planned for GitHub. Google Play is deferred. The
roadmap's formal M6 and M7 definitions remain unchanged; a GitHub release does
not by itself close those milestones.

## Current preparation

- Version `1.0.0` (version code `1`) is configured in the application build.
- On 2026-11-19 the owner reported successful Fold testing. A signed ARM64
  release was installed and launched on the owner's SM-F976B. This report does
  not establish individual performance measurements or the week-long reading
  checkpoint.
- The owner also accepted the device checks on 2026-11-01 without recorded
  performance numbers; see [M6 performance](perf/M6.md).
- `assembleRelease` and `checkPolicy` passed locally; the signed APKs were
  verified with `apksigner`. Reverify the exact artifacts and commit selected
  for publication.
- PRF-02 to PRF-05 device measurements and the week of reading remain without
  recorded evidence; see [M6 performance](perf/M6.md) and the [roadmap](../ROADMAP.md).
- The release keystore is backed up outside the repository. Keep its password
  separately and protect both; losing the key prevents ordinary in-place
  updates for GitHub installs.

## Before publishing on GitHub

1. Review the intended public repository and its complete Git history for
   secrets, personal information, licensing, and provenance. Resolve findings
   before changing visibility. A clean scan is useful evidence, not a guarantee.
2. Review and commit the final documentation and select the release commit.
   Confirm CI and run `verifyRoborazziDebug` against that commit.
3. Build with the release key and verify every APK with `apksigner verify
   --print-certs`. Confirm all artifacts have the expected signing certificate
   and version. Never commit the key or passwords. The build reads
   `BIFOLIO_KEYSTORE`, `BIFOLIO_KEYSTORE_PASSWORD`, `BIFOLIO_KEY_ALIAS`, and
   `BIFOLIO_KEY_PASSWORD` from the environment or untracked `local.properties`.
4. Test installation and launch of the exact signed ARM64 artifact on the Fold.
   A debug-signed install cannot be updated by a differently signed release;
   uninstalling it removes its local data.
5. Prepare release notes and SHA-256 checksums for the three ABI APKs. Review
   the README, privacy policy, LICENSE, NOTICE, and PROVENANCE.
6. With the owner's approval, make the repository public, tag the selected
   commit `v1.0.0`, and publish the GitHub release with the signed APKs,
   checksums, and notes. Confirm the public links and downloads.

`arm64-v8a` serves most modern Android phones, `armeabi-v7a` 32-bit ARM, and
`x86_64` compatible x86-64 devices and emulators. There is no universal APK.

## Later: Google Play

A Play release needs its own readiness review: developer account and app entry,
Play App Signing and upload-key decisions, signed AAB, listing assets, privacy
policy URL, data safety and content-rating forms, and any applicable testing
requirements. Check current Play requirements at that time. Distribution via
GitHub does not require completing these steps now.
