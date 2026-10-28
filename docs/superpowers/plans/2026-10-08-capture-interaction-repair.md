# Capture interaction repair

This executes the approved manual floating controls and persistent swipe guide (V5). No new product design approval is needed.

## Required behavior

- Both modes use the same floating control, drag, edge docking, collapse, Start and Finish interaction.
- Manual preparation defaults to the floating control. Missing overlay permission opens preparation; notifications are an explicit fallback.
- Automatic setup selects AUTO. A prepared manual session can be cancelled when switching; active capture cannot be switched.
- A manual session never changes the user's automatic overlay preference.
- First accepted screen shows the guide; subsequent joins retain the faint route. Guides and controls are excluded from saved frames.

## Execution and verification

- [x] Add failing control-routing and actual Activity setup regression checks.
- [x] Correct permission selection and mode/session cleanup.
- [x] Reuse FloatingOverlayManager and LoomFloatingBubble for manual capture; retain guide windows.
- [x] Verify unit, Android UI, build and lint checks.
- [x] Run real manual capture, persistent guidance, Finish/preview, then automatic capture and repeat switching on the connected Android 10 phone.
- [x] Restore the current debug data backup and temporary permissions; retain review screenshots and APK.

Device verification also exposed a repeated-content seam mismatch and a prepared-session help action that did nothing. Both were reproduced, repaired and covered by regression checks. Verification evidence is recorded in [the completion report](../verification/2026-10-08-capture-interaction-repair.md).

Formal package org.scrollloom and its data are outside this test scope. Preserve all existing workspace changes.
