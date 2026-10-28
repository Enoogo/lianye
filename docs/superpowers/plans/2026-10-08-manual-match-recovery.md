# Manual matching and recovery implementation plan

> Execute inline using executing-plans. The user has already authorized implementation; preserve the existing dirty branch and do not commit unrelated work.

**Goal:** Match actual scroll content and recover from transient unmatched frames without losing or inventing content.

**Architecture:** `ManualFrameAnalyzer` owns image evidence and fixed edge detection. `LoomEngine` retains the last reliable anchor while unmatched. The service and floating feedback display recoverable waiting and the existing guide.

**Tech Stack:** Kotlin, coroutines, JUnit, native Android MediaProjection, ADB.

## Tasks

- [x] Preserve current capture using Finish and archive debug `shared_prefs` and `files/drafts`.
- [x] Add analyzer regressions in `ManualFrameAnalyzerTest.kt`: large fixed navigation, local/large animation, temporal badge evidence, collapsing chrome, repeated unique headings and ambiguous periodic rows.
- [x] Replace freezing regression in `ManualLoomEngineTest.kt` with default-session recovery: unrelated samples followed by overlapping content, callbacks `[true, false]`, exact output pixels unchanged.
- [x] Confirm new failure regressions before implementing their fixes, including native repeated paragraph frames, large temporal video, translucent footer and hidden Finish hit target.
- [x] Implement region-aware matching with temporal animation masks, absolute evidence minimum and fine local feature conflict checks. Keep personal app frames out of committed test resources.
- [x] Remove the unmatched freeze branch from `LoomEngine.kt`; keep the anchor and use `onUnmatched` on state changes. Remove obsolete call arguments and production manual GAP transition.
- [x] Display `暂未接上，向下滑回一点` while waiting; restore guide after reliable matching. Validate `ManualSessionFeedbackTest.kt`.
- [x] Fix stale intermediate capture buffers, transparent Finish hit targets and translucent footer duplication exposed by device QA.
- [x] Run all unit tests, build APK and Android tests, lint. Install debug update only: 161 unit tests and 20 native tests pass; lint has 0 errors, 88 existing warnings.
- [x] On Huawei Android 10 verify generated fixed navigation/dynamic content, skipped-content rejection and recovery, multiple manual sessions, Bilibili collapsing/translucent navigation, and manual-to-auto capture without restarting the app. Check output heights and continuity.
- [x] Restore user draft/preferences byte-for-byte (9 files), overlay allow/accessibility off and projection stopped. Remove test helper APK and temporary device archives. Updated debug APK remains installed; formal package unchanged.
- [x] Record evidence in `docs/superpowers/verification/2026-10-08-manual-match-recovery.md`.
