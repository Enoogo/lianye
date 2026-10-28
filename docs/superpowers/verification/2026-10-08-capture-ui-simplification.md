# Capture UI simplification verification — 2026-10-08

## Delivered behavior

- A saved capture no longer creates a Continue editing entry on home. Launcher re-entry from its preview returns to a fresh home, and starting another capture does not ask to replace the saved image. Unsaved work still has recovery and replacement protection.
- Manual and automatic floating controls share Start and Finish. The recording control contains only Finish; screen counts, the progress dot, extra status text, and ready/guide close buttons are removed.
- Manual guidance uses dark, white, and accent strokes for its route, arrow, and endpoints. Labels have contrasting text outlines. The route remains after a successful join. The vertical gesture runs from 74% to 37% of screen height, leaving more overlap after target-app inertia.
- Help explains the currently selected mode. Automatic help explicitly says the app scrolls; manual help explains swipe, pause, and Finish. The six OEM setting paths and restricted-settings remedy remain. The More troubleshooting section is removed.
- Privacy and about are reduced to local processing/no upload, version, open source, and no ads. View updates opens the existing GitHub releases page in a browser. There is no automatic download, update check, network dependency, or INTERNET permission.
- Home artwork now depicts joined page fragments with a small seam rather than a circular accent.

## Overlay capture defect

User confirmed the contamination was in the app-generated manual image, not an OS screenshot. A stationary capture on the previous build did not reproduce the reported intermittent output contamination, so there is no claim that the user's original image was reproduced.

The regression did identify a concrete rendering gap: hiding the root with `alpha = 0` still allowed `View.draw(Canvas)` to draw **109,399 nontransparent pixels** on this device. After the fix, the software canvas contains zero control pixels. The native screen over the control bounds also matches the solid fixture background, while a pointer tap still invokes Finish.

The capture transaction now drains old queued buffers before hiding, explicitly skips control drawing, hides decoration windows, waits for two transparent window submissions, selects a frame newer than the first submission, and awaits restoration on the main thread. Restoration also runs in `NonCancellable` cleanup. A static page's final clean buffer is retained rather than drained after hiding. Raw monitoring samples are never substituted for clean capture frames.

An independent read-only agent reviewed the frame acquisition/window lifecycle and reported no blocking race. Its cancellation cleanup recommendation was included.

## Automated verification

Final command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
```

Result: **BUILD SUCCESSFUL in 23 s**. Unit reports contain **161 tests across 24 suites**, zero failures/errors. Lint reports zero errors and **90 warnings**; this is not a warning-free build. The dependency guard found zero forbidden network dependencies.

Native suite: **21 tests passed** after the main capture/UI changes, in 37.62 s. The final guide-distance and cancellation cleanup changes were then checked with the targeted UI/window suite:

```powershell
adb shell am instrument -w -e class org.scrollloom.ui.main.UiVisualReviewTest,org.scrollloom.ui.main.FloatingCaptureVisibilityTest org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **OK (5 tests), 15.491 s**. Tests render light/dark UI, 1.8× English text at a 320 dp content width, mode help, permission preparation, replacement controls, persistent manual guidance, and capture-hidden touchability. Saved home regression failed on the previous build and passed after the saved-entry fix; the existing unsaved crop/recreation test remains passing.

`git diff --check` returned exit code 0. Git printed existing working-copy LF/CRLF conversion warnings.

## Real-device checks

Device: Huawei STK-AL00, Android 10/API 29, 1080 × 2340, density 480.

| Check | Observed result |
| --- | --- |
| Stationary manual capture | 1080 × 2340. Content pixels match an overlay-free golden over `(0, 100, 1068, 2280)`. Clock/system scrollbar edges are excluded from the comparison. |
| Three guided manual swipes on an animated target | Each gesture `(777, 1732) → (777, 866)` over 1000 ms, with pauses. Matcher accepted forward deltas **1135, 1129, 1131**. Output **1080 × 5735**, with consecutive content sections and fixed header/footer retained once. |
| Overlay contamination | Zero sampled Finish, guide outline, accent, or blended guide palette pixels in stationary and animated outputs. Whole animated result inspected visually. |
| Save/export | Actual gallery PNG is pixel-identical to the assembled animated draft, including size **1080 × 5735**. |
| Saved re-entry | Fresh Start capture home; no Continue editing prompt. Starting again enters preparation directly. |
| Manual → automatic | Saved manual image, returned home, selected Auto and authorized projection, then started through the same floating control without restarting the app. Automatic scrolling and Finish worked; output **1080 × 15855** with zero sampled overlay palette pixels. |
| Guide persistence/readability | Full labels before the first join; route remains afterward. Contrasting outlines inspected on white, tinted, text-heavy, and dark renders. |

The earlier guide ending at 31% height left too little overlap on the animated fixture after inertia and later swipes did not join. The final 37% endpoint passed all three guided swipes; the failed trial is not counted as successful capture evidence.

Local QA artifacts are in ignored `build/ui-simplification/`:

- `final-home.png`
- `animated-guide-final.png`, `animated-route-final.png`
- `animated-gallery-result.png`
- `static-golden.png`, `static-result.png`
- `auto-running.png`, `auto-result.png`
- `design-final/ui-polish/` for the final 14 native UI renders
- `user-backup.tar`, `restored-user-final.tar` for restoration hashes

## User-state restoration

The fresh backup for this task contained three preference files and no draft files. All three files were restored byte-for-byte and compared by SHA-256, including after final launcher re-entry. The earlier task's backup was not used.

Final device state:

- Updated debug app installed; formal package untouched.
- Accessibility services setting restored to `null`, accessibility enabled restored to `0`.
- MediaProjection is `null`; no temporary capture session remains.
- Initial debug overlay permission remains `allow`.
- Original manual mode, learned-guide flags, and floating position restored.
- QA helper APK uninstalled; QA screenshot directories and temporary pushed files removed.
- Only this task's QA gallery images (media IDs 723, 727, 728) deleted. Both pre-existing gallery images remain.

## Limits and update policy

Only the connected Huawei Android 10 device received live capture acceptance. Other OEM paths remain in the UI, but this round does not claim live coverage of every OEM or Android version. Contrast strokes improve visibility; no universal claim is made for every possible patterned background. Pixel checks cover known fixture colors plus visual inspection, not an exhaustive proof over all apps.

Updates remain deliberately lightweight: show the installed version and open `https://github.com/gxwane/scroll-loom/releases` on user action. Future APK releases must increment versionCode and use the same signing identity for installation over an existing release. No release was published in this task.
