package org.lianye.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lianye.R
import org.lianye.domain.model.WeavingState

class MainUiStateTest {

    @Test
    fun create_whenConnected_phaseIsHidden() {
        val state = MainUiState.create(
            weavingState = WeavingState.Idle(isServiceConnected = true),
            isProjectionGranted = true,
            hasAttemptedEnable = true,
            hasEncounteredRestriction = true,
            isRestrictedBlocked = true,
            preFlightHintRes = R.string.vendor_hint_aosp
        )
        assertTrue(state.isServiceConnected)
        assertEquals(RestrictedCardPhase.HIDDEN, state.restrictedCardPhase)
    }

    @Test
    fun create_whenNotAttempted_phaseIsHiddenEvenIfBlocked() {
        // Cold start: user hasn't tapped enable yet. MUST be zero noise.
        val state = MainUiState.create(
            weavingState = WeavingState.Idle(isServiceConnected = false),
            isProjectionGranted = true,
            hasAttemptedEnable = false,
            hasEncounteredRestriction = false,
            isRestrictedBlocked = true,
            preFlightHintRes = R.string.vendor_hint_aosp
        )
        assertFalse(state.isServiceConnected)
        assertEquals(RestrictedCardPhase.HIDDEN, state.restrictedCardPhase)
    }

    @Test
    fun create_whenAttemptedAndBlocked_phaseIsBlockedGuide() {
        // User attempted, got blocked by system, returned to app
        val state = MainUiState.create(
            weavingState = WeavingState.Idle(isServiceConnected = false),
            isProjectionGranted = true,
            hasAttemptedEnable = true,
            hasEncounteredRestriction = false,
            isRestrictedBlocked = true,
            preFlightHintRes = R.string.vendor_hint_aosp
        )
        assertEquals(RestrictedCardPhase.BLOCKED_GUIDE, state.restrictedCardPhase)
    }

    @Test
    fun create_whenEncounteredAndNowUnblocked_phaseIsAllowedReadyReturn() {
        // User went to App Info, allowed restricted settings, returned to app
        val state = MainUiState.create(
            weavingState = WeavingState.Idle(isServiceConnected = false),
            isProjectionGranted = true,
            hasAttemptedEnable = true,
            hasEncounteredRestriction = true,
            isRestrictedBlocked = false,
            preFlightHintRes = R.string.vendor_hint_aosp
        )
        assertEquals(RestrictedCardPhase.ALLOWED_READY_RETURN, state.restrictedCardPhase)
    }

    @Test
    fun create_whenAttemptedAndNotBlocked_phaseIsHidden() {
        // Normal device (Android 10-12, or installed from trusted store/ADB):
        // User attempted, returned without enabling, but is NOT restricted.
        val state = MainUiState.create(
            weavingState = WeavingState.Idle(isServiceConnected = false),
            isProjectionGranted = true,
            hasAttemptedEnable = true,
            hasEncounteredRestriction = false,
            isRestrictedBlocked = false,
            preFlightHintRes = R.string.vendor_hint_aosp
        )
        assertEquals(RestrictedCardPhase.HIDDEN, state.restrictedCardPhase)
    }

    @Test
    fun create_whenProjectionGranted_showMediaProjectionCardIsFalse() {
        val state = MainUiState.create(
            weavingState = WeavingState.Idle(isServiceConnected = false),
            isProjectionGranted = true,
            hasAttemptedEnable = false,
            hasEncounteredRestriction = false,
            isRestrictedBlocked = false,
            preFlightHintRes = R.string.vendor_hint_aosp
        )
        assertFalse(state.showMediaProjectionCard)
    }
}
