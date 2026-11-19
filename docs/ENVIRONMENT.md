# Development Environment

Everything needed on a workstation to develop, test and benchmark Bifolio.

## Hardware

| Item | Minimum | Recommended | Why |
|---|---|---|---|
| RAM | 16 GB | 32 GB | Gradle, Android Studio and a foldable emulator together |
| Disk | 50 GB free SSD | 100 GB | SDK, emulator images, Gradle cache |
| CPU | Hardware virtualization enabled (VT-x, AMD-V or Apple Silicon) | 8+ cores | Emulator performance |
| Test device | Owner's foldable with Developer options and USB debugging | Wireless debugging paired | Benchmarks, motion and typography review |

## Software

| # | Component | Version | Check |
|---|---|---|---|
| 1 | Git | current | `git --version` |
| 2 | GitHub CLI, authenticated | current | `gh auth status` |
| 3 | JDK 21 (Temurin or Android Studio's bundled JBR) | 21 | `java -version`; `JAVA_HOME` set |
| 4 | Android Studio | latest stable | first-run setup completed |
| 5 | Android SDK Platform 37, Build-Tools, Platform-Tools | API 37 | `adb --version`; `ANDROID_HOME` set |
| 6 | Emulator with images: Android 17 phone, Pixel Fold profile, resizable emulator | API 37 | a foldable AVD boots and folds |
| 7 | Claude Code | current | `claude --version` |
| 8 | `jq` (used by Claude Code hooks) | current | `jq --version` |
| 9 | `ktlint` CLI (optional, used by the format hook) | current | `ktlint --version` |
| 10 | `scrcpy` (optional, recommended) | current | mirrors the device for motion review and recording |

Gradle is provided by the wrapper. Docker is not needed.

### Operating system notes

- **macOS (Apple Silicon):** smoothest path; arm64 emulator images.
- **Windows:** run Claude Code and Android Studio natively (not in WSL). Install Git for Windows, which provides the bash used by hooks; install `jq` (for example with winget).
- **Linux:** enable KVM and add your user to the `kvm` group.

## Device preparation

1. Developer options: USB debugging on; Wireless debugging paired with `adb pair`.
2. `adb devices` lists the device as `device`.
3. For benchmarks: unlocked, long screen timeout, battery saver off, charging.

## Accounts and secrets

| Item | Notes |
|---|---|
| GitHub repository `idcesares/bifolio` | Private until the first release, then public |
| Google Play Console developer account | Deferred; not required for GitHub distribution. Check current requirements if publishing on Play later. |
| Release keystore | Keep a secure backup of the keystore and store its password separately. Never commit either. The release build reads `BIFOLIO_KEYSTORE`, `BIFOLIO_KEYSTORE_PASSWORD`, `BIFOLIO_KEY_ALIAS` and `BIFOLIO_KEY_PASSWORD` from the environment or `local.properties`; without them `assembleRelease` produces unsigned APKs |
| `bifolio@dcesares.dev` | Play contact and privacy policy contact |

## Quick check

```bash
git --version
gh auth status
java -version
adb --version
adb devices
claude --version
jq --version
```
