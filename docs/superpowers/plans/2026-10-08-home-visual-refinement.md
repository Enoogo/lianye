# Home visual refinement implementation plan

> **For agentic workers:** Use executing-plans to implement this plan in the existing feature branch. Preserve inherited working-tree changes and user data; no commit or publication.

**Goal:** Apply the approved homepage visual improvements and inspect actual Android renders.

**Architecture:** Keep MainScreen callbacks and state routing intact. Extract the homepage illustration into a focused Canvas component, adjust only homepage typography/alignment, lighten the existing selectable control, and render the same brand geometry using theme colors in the header.

**Tech Stack:** Kotlin, Compose, Canvas, Android instrumentation, ADB.

## Task 1: Preserve the device and record the baseline

- [x] Back up current `shared_prefs` and `files/drafts` into `build/home-visual-refinement/user-backup.tar` before installing or testing; record gallery names, accessibility settings and current screen.
- [x] Review `MainScreen.kt`, `MainHeader.kt`, `CaptureModeSelector.kt` and existing homepage/entry instrumentation checks. Use the approved spec in `docs/superpowers/specs/2026-10-08-home-visual-refinement-design.md`.

## Task 2: Refine the homepage visuals

- [x] In `MainScreen.kt`, use local 24sp / 32sp headline and 14sp / 21sp caption styles. Remove the Chinese forced line break. Center the empty-home content group within a scrollable minimum-height viewport. Keep draft layout, all callbacks and status text intact.
- [x] Create `app/src/main/java/org/scrollloom/ui/main/components/LongCaptureIllustration.kt`: draw two vertically aligned fragments with continuous margins/content and short side connections. Use theme surfaces, fine strokes and the brand accent; replace the old private illustration in MainScreen.
- [x] In `MainHeader.kt`, align the visible logo to the 24dp content edge, use theme-colored vector paths for the existing logo, remove the solid background badge and constrain the header to the same 560dp grid.
- [x] In `CaptureModeSelector.kt`, remove the filled segmented container. Retain TextButton hit areas, selection semantics and callbacks; use a 24dp × 2dp indicator and restrained text styles for the chosen mode.
- [x] Extend the existing native visual review with normal Chinese dark and large Chinese at 320dp, keeping all modes and the primary action reachable. No new unit tests are needed for these reversible visual changes.

## Task 3: Render and verify

- [x] Run `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline`. Inspect build results and lint errors.
- [x] Install the debug and instrumentation APKs and run the complete existing Android instrumentation suite. Check the new homepage variants and existing entry, draft and floating interactions.
- [x] Pull real native renders, inspect typography, joins, spacing, clipping and theme contrast; refine based on those images if necessary. Capture the actual app homepage after data restoration.
- [x] Restore the fresh backup and compare every file by SHA-256, including after the final launch. Confirm gallery names and permissions are unchanged, projection is released, and remove this task's helper package and phone QA directories.
- [x] Record evidence in `docs/superpowers/verification/2026-10-08-home-visual-refinement.md`, check the completed steps and deliver the actual homepage image.
