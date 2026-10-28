# Home word composition implementation plan

> **For agentic workers:** Use executing-plans inline in the existing feature branch; preserve inherited changes and user data, without commits or publication.

**Goal:** Replace the rejected homepage slogan and punctuation with a concise, composed native graphic and word group.

**Architecture:** Add a focused HomeEmptyHero composable, keep MainScreen state routing untouched, and refine the existing Canvas illustration. Existing footer controls and draft content retain their actions.

**Tech Stack:** Kotlin, Compose, Canvas, Android instrumentation and ADB.

## Preserve the device

- [x] Create a fresh `build/home-word-composition/user-backup.tar` for `shared_prefs` and `files/drafts`, save gallery names and permissions, and inspect the current homepage before installing.

## Implement the visual composition

- [x] Create `app/src/main/java/org/scrollloom/ui/main/components/HomeEmptyHero.kt`: compose a 160dp × 284dp illustration and a two-level word group in a centered Row with 24dp spacing when width is at least 280dp and font scale is below 1.4; otherwise use a centered Column. Use Chinese “连成” / “长图” and English “One long” / “image”, without punctuation; mark the words as one accessible heading.
- [x] Update only the empty-home branch in `MainScreen.kt` to render HomeEmptyHero. Keep the draft title/card, scrollable viewport, all conditional feedback, footer and callbacks.
- [x] Refine `LongCaptureIllustration.kt` to a 148dp × 264dp page composition, vertically center the drawing within its Canvas, use restrained text rows and an image block, and keep short side joins and theme colors.
- [x] Use existing UiVisualReviewTest renders to inspect normal/large-font, light/dark and both modes. Do not add tests that mirror style values.

## Verify and restore

- [x] Run `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug verifyZeroNetworkDependencies --offline`; inspect build, lint and dependency results.
- [x] Install debug and helper APKs, run the complete Android instrumentation suite, pull screenshots into `build/home-word-composition/native-final` and `draft-final`, and inspect the final native layout. Refine only when native renders reveal a concrete problem.
- [x] Restore this task's fresh backup, compare SHA-256 for every file before and after final launch, check gallery and permission state, and remove this task's helper, QA directories and temporary phone files.
- [x] Capture `build/home-word-composition/after-home.png`, write `docs/superpowers/verification/2026-10-08-home-word-composition.md`, and report the real device result.
