package org.lianye.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.lianye.service.LianyeAccessibilityService

class AccessibilitySettingsNavigatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun serviceDetailsAndHighlightedFallbackTargetOnlyOurOwnService() {
        val attempts = mutableListOf<Intent>()
        val destination = AccessibilitySettingsNavigator.launchCandidates(context) {
            attempts += it
            if (attempts.size == 1) throw SecurityException("System-only details activity")
        }
        val own = ComponentName(context, LianyeAccessibilityService::class.java)
        @Suppress("DEPRECATION")
        assertEquals(own, attempts[0].getParcelableExtra<ComponentName>("android.intent.extra.COMPONENT_NAME"))
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, attempts[1].action)
        assertEquals(own.flattenToString(), attempts[1].getStringExtra(":settings:fragment_args_key"))
        assertEquals(own.flattenToString(), attempts[1].getBundleExtra(":settings:show_fragment_args")
            ?.getString(":settings:fragment_args_key"))
        assertEquals(AccessibilitySettingsNavigator.Destination.ACCESSIBILITY, destination)
        assertEquals(2, attempts.size)
    }

    @Test fun missingOrProtectedRoutesFallBackToPlainSettingsInOrder() {
        val attempts = mutableListOf<Intent>()
        val destination = AccessibilitySettingsNavigator.launchCandidates(context) {
            attempts += it
            if (attempts.size < 4) throw ActivityNotFoundException("Unavailable vendor route")
        }
        assertEquals(4, attempts.size)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, attempts[2].action)
        assertNull(attempts[2].extras)
        assertEquals(Settings.ACTION_SETTINGS, attempts[3].action)
        assertTrue(attempts.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
        assertEquals(AccessibilitySettingsNavigator.Destination.SETTINGS, destination)
    }

    @Test fun noSettingsActivityReturnsUnavailableWithoutCrashing() {
        var attempts = 0
        val result = AccessibilitySettingsNavigator.launchCandidates(context) {
            attempts++
            throw SecurityException("Settings disabled by device policy")
        }
        assertEquals(4, attempts)
        assertEquals(AccessibilitySettingsNavigator.Destination.UNAVAILABLE, result)
    }
}
