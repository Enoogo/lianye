# Home viewfinder visual implementation plan

> **For agentic workers:** Execute inline with executing-plans in the existing feature branch. Preserve inherited edits and current device data; no commit or publication.

**Goal:** Give the homepage a functional brand composition through a continuous screenshot extending beyond one screen.

**Architecture:** Replace the two-card Canvas with a continuous page and viewfinder geometry; simplify HomeEmptyHero to this single responsive illustration. MainScreen actions and state routing remain unchanged.

**Tech Stack:** Kotlin, Compose Canvas, Android instrumentation, ADB.

## Device preservation

- [x] Save a fresh backup of `shared_prefs` and `files/drafts` to `build/home-viewfinder/user-backup.tar`, record gallery names and permissions, and capture the baseline before installing.

## Visual implementation

- [x] In `HomeEmptyHero.kt`, replace the word group and layout branches with `LongCaptureIllustration(Modifier.fillMaxWidth().height(344.dp))`.
- [x] In `LongCaptureIllustration.kt`, use a 300 × 328 design grid scaled uniformly to the Canvas. Draw a continuous 128 × 260 page within four 222 × 164 viewfinder corners; let the page continue below the frame. Use a soft two-layer offset shadow, fine paper outline, a custom curved image, restrained article rows, and a second content block. Finish the lower page outline in the brand accent, with short connection marks at the screen boundary.
- [x] Inspect an actual normal-font render before running the suite. Refine only if the native image reveals a concrete proportion, readability or meaning problem.

## Verify and restore

- [x] Run `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline`; inspect build, lint and guard results.
- [x] Install the debug and helper APKs, run all Android instrumentation tests, pull native screenshots, and inspect both modes, dark, Chinese/English large font at 320dp, and the retained draft page.
- [x] Restore this task's fresh backup, verify every file by SHA-256 both before and after final launch, check gallery and permission state, and remove the helper and named phone QA artifacts.
- [x] Save `build/home-viewfinder/after-home.png`, write `docs/superpowers/verification/2026-10-08-home-viewfinder.md`, and deliver the actual updated homepage.
