# Capture entry simplification implementation plan

**Goal:** Apply the user's corrections in [the capture entry design](../specs/2026-10-08-lianye-capture-entry-design.md).

**Architecture:** Home owns capture mode selection. Preparation owns only the next required permission. Manual service has one floating control and a status-only foreground notification. Existing repository guards keep active sessions and drafts safe.

**Tech Stack:** Kotlin, Jetpack Compose, Android Activity results, MediaProjection.

- [x] Back up the current debug preferences/draft and permission state before device checks.
- [x] Add Android entry regressions against the old implementation: modes visible without Help, no notification control choice, no mode change in permission sheets.
- [x] Move mode selection from `AboutBottomSheet.kt` to `MainScreen.kt`, remove duplicate help/home task entrances, retain mode-specific help.
- [x] Simplify `CapturePreparationSheets.kt` to one permission action each. In `MainActivity.kt`, request overlay before projection and continue on return, preserving cancellation and mode guards.
- [x] Remove service notification-control selection and start/finish notification workflow from `LoomMediaProjectionService.kt`; simplify `ManualControlPolicy.kt` to overlay availability.
- [x] Update real Activity mode/prepared-session regressions and `UiVisualReviewTest.kt` for the new entry behavior.
- [x] Run `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`, direct Android instrumentation, and `git diff --check`.
- [x] Review actual home/preparation/help screenshots, verify overlay setting → projection continuation, restore this round's debug snapshot and temporary grants, install the newest test APK and record evidence.

Completion evidence: [capture entry verification](../verification/2026-10-08-lianye-capture-entry.md).
