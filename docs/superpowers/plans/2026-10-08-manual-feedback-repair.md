# Manual capture feedback and floating UI repair

> Execute inline with systematic debugging and verification-before-completion. An independent read-only audit was requested, but its turn failed at the provider usage limit; root performs the code review. Preserve the dirty working tree and the fresh user backup.

**Goal:** Explain a real stable-frame join failure immediately, recover without losing the valid prefix, and replace layered floating controls and transparent text pills with compact consistent surfaces.

**Design:** Keep one shared Start/Finish control for both modes. Remove the double container around Finish. Place any failure sentence inside that same opaque, touchable control window, above Finish; no count, Cancel, or extra action. Prepared mode needs only Start. The gesture guide uses a thin contrasting route and small endpoints; its two initial labels use dark contrast badges rather than paper/outlined text. On join failure the upward route hides and the instruction says to swipe down slightly, pause, then continue. The failure remains until content is accepted or the accepted starting position is reached.

**Architecture:** A failed match already follows stable raw samples and a clean position-confirmed capture; report this confirmed failure without waiting for a second identical failed position. Maintain enough temporal image evidence to exclude actual changing content, while keeping unique stable headings and ambiguity rejection. Use generated samples for committed regression fixtures; keep any real app pixels only in ignored local QA files.

## 1. Diagnose and protect data

- [x] Read the actual device log and current preview. First failure: best shift 850, residual 1.796, local conflict 11/43; later positions have no overlap evidence.
- [x] Back up current user preferences and draft into `build/manual-feedback-fix/user-backup.tar`; preserve the original gallery and formal app.
- [x] Trace feedback: `UNMATCHED_CONFIRMATIONS` requires repeated failure at the same position, so continued swipes/dynamic scenes can prevent any callback.
- [x] Capture stable before/after observations on the actual target; compare local conflict against genuine scrolling evidence before changing analyzer decisions.

## 2. Failure feedback regression

Files: `app/src/test/java/org/scrollloom/engine/ManualLoomEngineTest.kt`, `app/src/main/java/org/scrollloom/engine/LoomEngine.kt`.

- [x] Add a test with two distinct stable unmatched positions and assert a failure callback on the first confirmed failed capture, while retaining the prefix and sampling for recovery. Run it red before fixing.
- [x] Report failure on the first clean stable unmatched position, retain notification state across subsequent changes, and clear it only on proven recovery. Remove the redundant confirmation count.
- [x] Run existing no-extra-frame, recovery, raw-window exclusion, secure-window, reverse, and cancellation regressions.

## 3. Actual dynamic content diagnosis

Files: `ManualFrameAnalyzer.kt`, `ManualFrameStability.kt`, `LoomEngine.kt`; tests `ManualFrameAnalyzerTest.kt` / `ManualLoomEngineTest.kt`.

- [x] Measure actual temporal changes and alignment. Add a generated regression for the identified cause and verify red before the smallest fix.
- [x] Keep periodic-content ambiguity and skipped-paragraph safeguards passing. Do not loosen global error thresholds or infer a seam from the gesture alone.

## 4. Floating surfaces

Files: `LoomFloatingBubble.kt`, `ManualFloatingOverlayManager.kt`, `FloatingCaptureVisibilityTest.kt`, `UiVisualReviewTest.kt`.

- [x] Use one clickable pill for Start/Finish with a 48 dp touch target. No nested button border or thick outer frame.
- [x] Render failure feedback inside the control window; remove the separate ready/failure tip window and repeated remove/add cycles. Finish remains the only capture action.
- [x] Thin the contrasting guide strokes, use small endpoints, and render initial labels as short dark badges with white type.
- [x] Add native assertions that failure copy and Finish are displayed together and remain until recovery; capture actual light/dark/detailed-background renders.

## 5. Acceptance and restoration

- [x] Run unit/build/lint/dependency checks and native tests. Inspect actual output and failure/recovery screenshots.
- [x] Repeat the real reported page, not only the generated text fixture. Document exact limits if any unresolved failure remains.
- [x] Restore every fresh user backup file by hash, retain formal app/gallery/initial permissions, stop temporary capture/accessibility, remove QA helper and known QA data.
- [x] Record evidence in `docs/superpowers/verification/2026-10-08-manual-feedback-repair.md` and deliver concise Chinese results with screenshots.
