# Capture UI simplification implementation plan

> **For agentic workers:** Use executing-plans for inline implementation; an independent read-only agent audits the capture-window race. Steps use checkbox syntax for tracking.

**Goal:** Start a fresh capture after saving, remove distracting controls and text, make manual guidance readable, and prevent our floating surfaces from entering manual output.

**Architecture:** Keep the existing native Compose screens, shared floating capture control, and MediaProjection session. Saved images stay in the gallery; only unsaved work gets a home resume entry. Capture-window visibility is owned and serialized by the acquisition lifecycle. Updates use an external release page, preserving the app's zero-network contract.

**Tech Stack:** Kotlin, Compose Material 3, Android WindowManager/MediaProjection, Android instrumentation, Gradle.

Preserve the current working changes, user files, formal app, overlay permission, and initial accessibility state. No commit, release publication, or new updater service is required.

### 1. Saved capture entry

Files: `app/src/main/java/org/scrollloom/ui/main/MainScreen.kt`, `MainActivity.kt`; test `app/src/androidTest/java/org/scrollloom/ui/main/MainActivityDraftTest.kt`.

- [x] Add a regression that creates a saved draft, navigates home/recreates, checks the absence of Continue editing, and taps Start capture to enter preparation without a replacement dialog. Preserve the existing unsaved crop/restoration test.
- [x] Run the new test on the existing app to confirm failure: `adb shell am instrument -w -e class org.scrollloom.ui.main.MainActivityDraftTest#savedCaptureReturnsToFreshHome org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner`.
- [x] Use `val currentDraft = (draft != null || hasDraft) && !(draft?.isSaved ?: isDraftSaved)` and route fresh primary actions through `requestNewCapture()`; launcher re-entry of a saved preview returns home.

### 2. One small capture control and persistent readable guidance

Files: `app/src/main/java/org/scrollloom/ui/floating/LoomFloatingBubble.kt`, `ManualFloatingOverlayManager.kt`, `app/src/main/java/org/scrollloom/service/capture/LoomMediaProjectionService.kt`, `app/src/androidTest/java/org/scrollloom/ui/main/UiVisualReviewTest.kt`.

- [x] Replace the capturing row with a padded 48 dp Finish button. Remove the progress dot, screen count, and extra status line; retain disabled Finish while finishing.
- [x] Remove the swipe-guide close window and its callback plumbing. Leave the shared prepared-capture control and essential Finish action available.
- [x] Draw the line, arrow, and endpoint circles three times: dark outer stroke, white middle stroke, and accent inner stroke. Use round caps/joins and opaque paint; keep the non-touchable window opacity below the Android input-obscuring limit. Keep route guidance after successful joins.
- [x] Inspect native guidance on light, dark, and detailed backgrounds, plus the existing touchability test.

### 3. Help, about, and home artwork

Files: `app/src/main/java/org/scrollloom/ui/main/components/AboutBottomSheet.kt`, `MainScreen.kt`, `README.md`.

- [x] Replace the numbered help list with the current mode's two concise instructions. Explicitly explain automatic scrolling; manual mode explains the swipe and pause. Preserve all six OEM setting paths and the restricted-settings remedy.
- [x] Remove More troubleshooting. Reduce privacy/about to local processing, no upload, version, and an external View updates link (`https://github.com/gxwane/scroll-loom/releases`), handling missing browsers with a short toast. No INTERNET permission or automatic download.
- [x] Replace the circular home artwork with two aligned content fragments joined into a continuous page, using neutral lines and a small accent seam.
- [x] Update the README's current product/interaction description so it does not promise removed privacy masking or old nested mode selection.

### 4. Manual capture overlay defect

Files: `app/src/main/java/org/scrollloom/service/capture/MediaProjectionFrameCapturer.kt`, `LoomMediaProjectionService.kt`, `app/src/main/java/org/scrollloom/ui/floating/FloatingOverlayManager.kt`, `ManualFloatingOverlayManager.kt`; instrumentation tests in `app/src/androidTest/java/org/scrollloom/ui/main/`.

- [x] Inspect and reproduce a static target's first frame, a scroll with guide visible, and Finish. User confirmed the defect is in app-generated manual long images, not OS screenshots. The static baseline did not reproduce the reported intermittent output residue; record the software-draw visibility regression and window-commit timing gap before changing capture behavior.
- [x] Add a meaningful regression for the identified lifecycle race. Make window rendering honor the capture-hidden state, and serialize restoration if asynchronous show can run after a later hide. Never substitute raw samples, paint over captured content, or introduce a black secure-window hole.
- [x] Repeat real manual capture with stationary and animated targets; inspect the whole saved result for ball/tip/guide remnants and verify Finish still receives a tap during transparent capture.

### 5. Verification and handoff

- [x] Run `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline` and inspect output/test reports.
- [x] Install debug and test APKs, run native instrumentation without simultaneous UI manipulation, and inspect generated home/help/guide screenshots in both themes and large text.
- [x] Verify a real save → app re-entry → new capture, compact Finish, manual output without overlays, and manual → automatic switching.
- [x] Restore the fresh user backup, compare every archived file by hash, stop temporary capture/accessibility, preserve the initial overlay permission, and remove only QA helper APK/data.
- [x] Record results and material limits in `docs/superpowers/verification/2026-10-08-capture-ui-simplification.md`, then provide a concise Chinese outcome and update recommendation.
