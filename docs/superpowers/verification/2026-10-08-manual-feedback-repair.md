# Manual join feedback and floating UI verification

## Diagnosis and changes

The device's reported session logged a first stable `Unmatched` with a promising 850 px shift but conflicting local features, followed by changing positions without sufficient overlap. The exact original failed candidate was not retained, so this investigation does not claim a pixel-for-pixel replay of that original train-video frame.

Two concrete defects were reproduced with failing regressions:

1. Failure feedback waited for two failed matches at an identical position, although each candidate had already settled across raw samples and passed clean-frame position confirmation. Continuing to scroll or changing video pixels reset the counter. A test with two distinct stable unmatched positions failed because the callback was still empty when the user moved again. It now reports the first confirmed stable failed match, retains the valid prefix, keeps sampling, and clears feedback on recovery.
2. Temporal masks covered raw sampling but omitted changes during the transparent-window fence and clean read. A generated moving badge that advanced during that interval caused a proved 80 px scroll to be rejected. Including observed changes between the position-confirmed raw/clean pair fixes the regression. The initial anchor also includes both its stable sampling interval and clean-read interval. Global match thresholds and fine-feature/ambiguity safeguards are unchanged.

The new control has one surface and one Finish action. The nested button/outer border is removed. A failure title and short recovery instruction sit inside the opaque control window; the separate transparent ready/failure tip window and repeated remove/add cycle are removed. Finish remains available. Gesture strokes and endpoints are thinner/smaller, and initial labels use short white text on dark contrast badges without text-outline clutter.

Native review caught an additional layout defect: widening a right-docked control could measure at the old button position and clip the notice. The manager now requests the full notice width and right-docked position before measurement. The native test waits for a fully in-bounds notice rectangle; actual failure text was inspected and is complete.

## Checks

Final build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
```

**BUILD SUCCESSFUL in 19 s.** **163 unit tests**, zero failures/errors. Lint has zero errors and 90 warnings. Network dependency guard passes with zero forbidden dependencies. `git diff --check` exits 0 with the existing working-copy LF/CRLF warnings.

Both added behavior regressions were run red before their respective fixes and green afterward. Existing periodic-content ambiguity, skipped-native-paragraph, secure-frame, cancellation, no-extra-frame-on-Finish, raw-window exclusion, and recovery regressions remain passing.

Native instrumentation:

```powershell
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

**OK (22 tests), 40.43 s.** The suite covers saved/unsaved entry, manual/auto routing, Finish touchability while transparent, first-frame/recording/retry controls, readable failure instruction plus Finish until recovery, theme/large-text screen renders, preview restoration, and export.

The first suite attempt revealed a test isolation issue: prepared-mode routing inherited an unsaved draft, whose recovery entry correctly took priority. Its fixture now discards prior test work before creating the prepared session. The full suite was rerun and passed. Native renders of the final failure notice are in `build/manual-feedback-fix/native-final/`.

An independent read-only audit was requested, but the subagent's provider usage limit prevented it from running. Its nonexistent review is not counted as verification.

## Actual reported-app acceptance

Device: connected Huawei STK-AL00, Android 10/API 29, 1080 × 2340. Tested the actual Bilibili home feed with thumbnails, a collapsing top bar, a translucent bottom bar, and a playing video card. Real feed images remain in ignored local QA files; they are not repository test fixtures.

| Action | Observed result |
| --- | --- |
| Three normal guided swipes, `(300,1732) → (300,866)`, 1000 ms, with pauses | Accepted forward deltas **1106, 1120, 1118**. |
| Two quick consecutive swipes, 450 ms, deliberately losing overlap | `Unmatched`; failure notice displayed with **未接上 / 向下滑回一点，稍停 / 结束**. Notice remained during subsequent changing video frames. |
| One downward recovery gesture | Accepted **584 px** of new content; failure notice disappeared and route/compact Finish returned. No restart or new capture was required. |
| Another normal guided swipe over the playing video feed | Accepted **1121 px**. |
| Finish and save | Produced **1080 × 7389**, eight tiles. Whole assembled output inspected: continuous feed, playing-video snapshot, retained header/footer, no visible own guide/control/notice. |
| Gallery output | Saved PNG has the same dimensions and is pixel-identical to the assembled draft. |

Local evidence:

- `bili-guide.png`: actual initial guidance
- `bili-gap.png`: actual failed join notice over a playing video card
- `bili-recovered.png`: route and compact Finish after recovery
- `bili-gallery-result.png`: actual saved long image
- `native-final/floating-ready.png`, `floating-guide.png`, `floating-unmatched.png`
- `user-backup.tar`, `restored-user.tar`: this task's fresh user backup and restored archive

The original frame pair's precise local conflict cannot be proven from its log alone. The late-animation defect is independently reproduced and fixed, and the reported app's successful scrolling, visible intentional failure, recovery, and continuing capture were directly observed. No claim is made that arbitrary fast swipes or every dynamically changing app can always be joined.

## Restoration

This task's fresh backup contained **eight files**: three preference files and the user's unsaved draft (three tiles plus primary/backup metadata). All eight restored files match the original by SHA-256. The older empty-draft backup was not used.

The updated debug app remains installed; the formal app is untouched. Accessibility setting remains `null` with enabled `0`; MediaProjection is `null`; initial overlay permission remains `allow`. QA helper APK, its UI screenshot directories, and known temporary pushed files were removed. Only this task's actual QA gallery image, media ID **737**, was deleted. All three images present before that save remain, including the user's newer saved image.

No commit, publication, release, or external message was sent.
