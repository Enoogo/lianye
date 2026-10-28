# Manual correction guidance verification

## Behavior

The previous temporary-unmatched state explicitly hid the screen guide. A confirmed reverse scroll shared the stationary branch and provided no directional feedback. Both gaps are addressed using the existing floating control and capture-hidden, non-touchable guide window.

- A settled unmatched candidate keeps `RECORDING` active with `ManualGuide.RECOVERY`. The shorter downward route has “向下滑回一点” at its start and “松手，稍停” at its end. Repeated failure and queued progress retain it until confirmed alignment returns.
- The floating control keeps “未接上” and Finish. The recovery guide carries the instruction, so the control omits duplicate copy and shrinks from 176dp to 104dp. The old textual instruction remains when recovery guidance is absent.
- A confirmed reverse match reports a separate callback, restores full upward instructions even for learned users, and leaves the accepted anchor, progress and output unchanged. Reverse recovery is not described as another error. Finish cannot be reopened by queued feedback.

## Extra issue found during actual-page acceptance

The first Bilibili run accepted an 823px scroll, correctly reported a -501px reverse, and accepted another 614px forward. Its large full-width playing video then changed scenes without moving the page, causing repeated `Unmatched` decisions in region `936..1670`. The recovery guide was visible, but the feedback itself was inappropriate for stationary content.

The zero-displacement decision did not use observed temporal masks, while shifted alignment already did. A video occupying about 40% of the viewport exceeded the layout-stability threshold. Analyzer and whole-session regressions were run red: the latter reported `[true]` unmatched and a failed completion for an unchanged page. They now pass.

The new stationary-only check ignores observed temporal regions and requires every retained sample to agree at zero displacement within the existing six-level pixel tolerance, at least 35% of interior samples retained, and informative evidence across three bands including the interior. A changed heading outside the animation mask must still prevent a stationary decision. This check never advances the anchor or appends pixels; shifted seam, ambiguity and local-feature thresholds are unchanged.

The exact original large-video scene-cut pair was not retained as a fixture. Bilibili refreshed its recommendations during the investigation, so the final real-page flow used the refreshed feed. The scene-cut correction is verified by analyzer and full-session pixel/progress regressions, not claimed as a replay of the original video pair.

## Automated checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline
```

BUILD SUCCESSFUL in 21 seconds. **168 unit tests**, zero failures/errors. The new feedback tests initially produced five behavior assertion failures before implementation. The two large-video regressions also failed before the stationary correction and passed afterward. Existing skipped-content, repeated-paragraph, ambiguity, output-pixel, stationary, reverse, cancellation and secure-frame checks pass.

After the last instrumentation-fixture adjustment, `:app:assembleDebugAndroidTest` and `:app:lintDebug` were rerun successfully. Lint reports zero errors and 90 existing warnings. The dependency guard finds zero forbidden network dependencies. `git diff --check` exits 0; working-copy LF/CRLF notices are captured locally.

```powershell
adb shell am instrument -w org.scrollloom.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Final result: **OK (24 tests), 42.694 seconds**, in `build/manual-correction-guide/native-complete-tests.txt`.

New native coverage checks that the recovery guide stays attached across repeated failure, exposes the correct instruction, remains non-touchable, restores forward instructions, and disappears at Finish. Clean-capture pixels across the screen's interior match the baseline exactly, including both the guide and floating notice regions. Existing Finish-hit-target coverage also passes.

Two instrumentation synchronization defects were identified while running the suite. Compose idle can precede first Surface presentation, so the baseline now waits for the actual magenta test frame. The saved-home test now waits for its animated preparation sheet to become visible after draft removal. Neither required a product navigation change. The complete suite was rerun after these fixes.

## Final real-device flow

Huawei STK-AL00, Android 10/API 29, 1080 × 2340, density 3. Manual capture used screen projection and overlays with accessibility still disabled.

| Action | Observed result |
| --- | --- |
| Ordinary upward swipe | Accepted **1119px**. |
| 400px downward swipe | Confirmed **Reverse**, -502px matching evidence. Full upward instructions reappeared without a failure notice or duplicate pixels. |
| Two fast upward swipes to lose overlap | `Unmatched`; the short downward correction route and compact “未接上 / 结束” control appeared. |
| Wait, then perform small downward gestures along the guide | Guidance persisted while still unmatched. Three 480px gestures eventually recovered a confirmed **951px** increment. |
| Continue normal capture | Accepted **1121px**; upward route and compact Finish remained available. |
| Finish | Produced **1080 × 5531**, six tiles. The assembled image was inspected: continuous cards and text, retained header/footer, no own control, correction notice or route pixels. |

Local evidence in `build/manual-correction-guide/`:

- `bili-reverse-final.png`: full forward instructions after reverse.
- `bili-gap-persistent.png`: actual correction guide, readable labels and compact failure control.
- `bili-recovery-step.png`, `bili-recovery-step-2.png`: persistent guidance before recovery.
- `bili-recovery-step-3.png`: cleared failure and restored upward route.
- `bili-result-final.png`: inspected completed long image.
- `native-final/`: native visual review renders.
- `final-real-page-log.txt`: matching/recovery decisions.

The correction route suggests a small gesture; it does not assert an exact distance to the accepted anchor. Only the connected Huawei device received live end-to-end testing. Arbitrarily fast or unsupported dynamic pages can still fail to provide reliable overlap, in which case the accepted prefix is retained and the corrective guide remains available.

## Restoration

This task's fresh backup contained **17 files**, including preferences and the user's current multi-tile draft, taken before any device mutation. All restored file contents match by SHA-256, both immediately after restoration and after the final app launch. No older backup was used.

All three existing gallery filenames are unchanged; this task did not save or delete any gallery image. Accessibility settings remain `null` and enabled `0`. Projection is released (`null`). The updated debug app remains installed; the formal app is untouched. The QA helper package, its two screenshot directories and the explicitly named temporary backup on the phone were removed.

No commit, release, publication or external message was sent.
