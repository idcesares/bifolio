# ADR-0008: No network access in v1

Status: Accepted
Date: 2026-10-15
Spec IDs: PRV-01 to PRV-06, OUT-03, OUT-04, NAV-08

## Context

Privacy is a core promise. Remote images can track readers, and cloud diagram rendering sends document content off the device.

## Decision

The v1 app declares no network permission. Remote images show a placeholder with alt text and the host name. External links are handed to the browser after confirmation. CI fails if the merged manifest contains a network permission.

## Consequences

- The Play listing and F-Droid entry can state "no network access" with no anti-features.
- Remote images (v1.1) will require a new ADR, an opt-in setting, and a manifest change reviewed explicitly.
