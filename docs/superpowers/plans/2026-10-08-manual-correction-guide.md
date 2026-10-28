# Manual correction guide implementation plan

> **For agentic workers:** Use executing-plans to implement the following tasks in this session. Preserve the existing uncommitted work and user data; do not commit or publish.

**Goal:** Show an actionable screen guide when manual capture cannot join or detects a backwards scroll.

**Architecture:** Keep the image matcher and accepted anchor unchanged. Extend the existing presentation state with a recovery guide, and report confirmed reverse positions through a separate engine callback. Reuse the existing non-touchable, capture-hidden guide window and the approved floating control.

**Tech Stack:** Kotlin, Android Canvas, Compose, coroutine engine, JUnit, Android instrumentation.

## Design

Unmatched settled content keeps the existing “未接上” notice and Finish control. The screen displays a short downward arrow labelled “向下滑回一点” and “松手，稍停”. While this guide is visible, the floating notice omits the duplicate instruction and uses a smaller 104dp width; otherwise it retains the existing text fallback. This is a suggested small recovery gesture, not a claimed exact distance to the accepted anchor. The guide stays until the image analyzer confirms overlap again.

A confirmed reverse position does not add duplicate content. It restores the full upward guide even after the user has learned the compact route. Returning to the accepted position keeps that hint until new content is accepted. A reverse match while recovering a gap is a recovery action, so it never displays an accusation or a new failure notice.

Ordinary motion and unchanged content are not errors. No additional options, counts, dismissal buttons, vibration, or blocking dialogs are added. All guide pixels still disappear before a clean frame; Finish remains available.

During real-page acceptance a large playing video (about 40% of the viewport) caused a stationary page to report Unmatched. The stationary decision previously ignored known temporal masks, although shifted alignment already used them. Add a strict zero-displacement check outside those observed regions: every retained sample must agree within six color levels, at least 35% of interior samples remain, and informative features are distributed across at least three bands including the interior. This only suppresses false movement/gap feedback and never appends pixels or lowers shifted seam thresholds. Add analyzer and full-session red/green regressions, including a changed heading outside the video mask.

## Task 1: Prove the missing feedback

- [x] Back up the current debug app preferences and draft before any device mutation into `build/manual-correction-guide/user-backup.tar`; record existing gallery names and accessibility settings.
- [x] In `ManualSessionFeedbackTest.kt`, require a persistent `RECOVERY` guide after `temporaryUnmatched(true)`, including learned users and repeat/queued progress; require full forward hints after `reversed()` and no reopening after Finish.
- [x] In `ManualLoomEngineTest.kt`, extend the existing reverse sequence test to assert a callback occurs once while output pixels and progress remain unchanged. Declare the default `onReverse` callback and an empty `reversed()` entry point first so failures demonstrate behavior rather than missing symbols.
- [x] Run `.\gradlew.bat :app:testDebugUnitTest --tests "*ManualSessionFeedbackTest" --tests "*ManualLoomEngineTest" --offline`; confirm the new behavior assertions fail.

## Task 2: Wire the corrective guide

- [x] Add `RECOVERY` to `ManualGuide` in `CaptureMode.kt`.
- [x] In `ManualSessionFeedback.kt`, select recovery guidance for unmatched overlay content, preserve it while unmatched remains active, and implement `reversed()` to restore full forward hints in an active session. Finish and unavailable/non-overlay controls keep guidance hidden.
- [x] In `LoomEngine.kt`, separate the existing Stationary/Reverse branch and call `onReverse()` only for a confirmed Reverse; keep the current clearing of unmatched state and unchanged anchor/output semantics.
- [x] In `LoomMediaProjectionService.kt`, route `onReverse` through the same owner/session check and Main dispatcher as existing feedback.
- [x] In `ManualFloatingOverlayManager.kt`, render the RECOVERY route downwards, shorter than the ordinary upward route, with readable endpoint labels and the same dark/white/color strokes. Update the guide's content description for assistive navigation. Retain capture hiding and touch-through opacity limits.
- [x] Update `UiVisualReviewTest.kt` to render the actual recovery guide and verify native labels survive repeated unmatched renders, recover to forward guidance, and disappear at Finish.
- [x] Reproduce and fix the stationary large-video false gap in `ManualFrameAnalyzer.kt`, with `ManualFrameAnalyzerTest.kt` and `ManualLoomEngineTest.kt`; retain unchanged content checks outside observed temporal regions.
- [x] Rerun the focused unit command, then build APKs, run the complete unit/native suites, lint and the zero-network guard.

## Task 3: Validate the complete user flow

- [x] On the connected Huawei device, verify a normal accepted scroll, a reverse scroll and upward correction, a deliberately lost overlap and downward corrective guide, persistent failure while waiting, then recovered upward guidance and Finish.
- [x] Inspect native screenshots for legibility, clipping and arrow direction. Check the result retains accepted content without overlay pixels.
- [x] Restore the fresh backup exactly; compare all tar file hashes. Restore initial accessibility settings, confirm projection is released, and remove only this task's QA artifacts/helper package.
- [x] Record results and relevant limits in `docs/superpowers/verification/2026-10-08-manual-correction-guide.md` and update the checkboxes above.
