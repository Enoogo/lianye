package org.lianye.platform

import android.app.AppOpsManager
import org.junit.Assert.*
import org.junit.Test

class AccessibilityHelpStateTest {
    @Test fun aConnectedServiceOverridesStaleRestrictionAndEnabledSnapshots() {
        val state = AccessibilityHelpState.resolve(true, false, RestrictionStatus.BLOCKED, 35)
        assertEquals(AccessibilityHelpStatus.CONNECTED, state.status)
        assertFalse(state.showRestrictedHelp)
    }

    @Test fun enabledButDisconnectedIsNotReportedAsReadyOrBlocked() {
        val state = AccessibilityHelpState.resolve(false, true, RestrictionStatus.BLOCKED, 35)
        assertEquals(AccessibilityHelpStatus.NOT_CONNECTED, state.status)
        assertFalse(state.showRestrictedHelp)
    }

    @Test fun definiteRestrictionDiffersFromAnUnknownProbe() {
        assertEquals(AccessibilityHelpStatus.RESTRICTED,
            AccessibilityHelpState.resolve(false, false, RestrictionStatus.BLOCKED, 33).status)
        assertEquals(AccessibilityHelpStatus.OFF,
            AccessibilityHelpState.resolve(false, false, RestrictionStatus.UNKNOWN, 33).status)
        assertTrue(AccessibilityHelpState.resolve(false, false, RestrictionStatus.UNKNOWN, 33).showRestrictedHelp)
    }

    @Test fun olderAndroidNeverShowsTheAndroid13RestrictedMenu() {
        for (api in 29..32) {
            val state = AccessibilityHelpState.resolve(false, false, RestrictionStatus.BLOCKED, api)
            assertEquals(AccessibilityHelpStatus.OFF, state.status)
            assertFalse(state.showRestrictedHelp)
            assertEquals(RestrictionStatus.NOT_APPLICABLE, RestrictedSettingsHelper.classify(api, AppOpsManager.MODE_ERRORED))
        }
    }

    @Test fun defaultMissingAndUnrecognizedAppOpsRemainUnknown() {
        assertEquals(RestrictionStatus.ALLOWED, RestrictedSettingsHelper.classify(33, AppOpsManager.MODE_ALLOWED))
        assertEquals(RestrictionStatus.BLOCKED, RestrictedSettingsHelper.classify(35, AppOpsManager.MODE_IGNORED))
        assertEquals(RestrictionStatus.BLOCKED, RestrictedSettingsHelper.classify(35, AppOpsManager.MODE_ERRORED))
        for (mode in listOf(AppOpsManager.MODE_DEFAULT, null, 99)) {
            assertEquals(RestrictionStatus.UNKNOWN, RestrictedSettingsHelper.classify(35, mode))
        }
    }
}
