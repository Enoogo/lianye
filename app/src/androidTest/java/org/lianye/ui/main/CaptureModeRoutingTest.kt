package org.lianye.ui.main

import android.app.Instrumentation
import android.content.IntentFilter
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.lianye.App
import org.lianye.domain.model.CaptureMode
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualControl
import org.lianye.domain.model.ManualPhase
import org.lianye.ui.common.localized

class CaptureModeRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeResumesAnAlreadyPreparedManualCaptureInsteadOfIgnoringTheTap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repository = App.instance.appComponent.lianyeRepository
        val originalMode = repository.captureMode.value
        val originalSession = repository.manualSession.value
        val home = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val monitor = instrumentation.addMonitor(home,
            Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null), true)
        try {
            // Prepared capture starts after draft replacement. Isolate this fixture from
            // an unsaved image left by another test or restored at application startup.
            assertTrue(repository.discardDraft())
            compose.runOnUiThread {
                assertTrue(repository.setCaptureMode(CaptureMode.MANUAL))
                repository.updateManualSession(ManualCaptureState(phase = ManualPhase.READY, control = ManualControl.OVERLAY))
            }
            compose.onNodeWithText(localized("去截图", "Go capture")).performClick()
            compose.runOnIdle { assertEquals(1, monitor.hits) }
        } finally {
            instrumentation.removeMonitor(monitor)
            compose.runOnUiThread {
                repository.updateManualSession(originalSession)
                repository.setCaptureMode(originalMode)
            }
        }
    }

    @Test fun automaticSetupAfterHomeModeSwitchActuallyUsesAutomaticMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repository = App.instance.appComponent.lianyeRepository
        val original = repository.captureMode.value
        val settingsRoutes = IntentFilter().apply {
            addAction("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            addAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            addAction(Settings.ACTION_SETTINGS)
        }
        val monitor = instrumentation.addMonitor(settingsRoutes,
            Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null), true)
        try {
            compose.runOnUiThread {
                assertTrue(repository.setCaptureMode(CaptureMode.MANUAL))
                compose.activity.getPreferences(0).edit().putString("capture_mode", "MANUAL").commit()
            }
            compose.onNodeWithText(localized("自动滚动", "Auto")).performClick()
            compose.onNodeWithText(localized("帮助", "Help")).performClick()
            compose.onNodeWithText(localized("前往设置", "Open settings")).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(1, monitor.hits)
                assertEquals(CaptureMode.AUTO, repository.captureMode.value)
                assertEquals("AUTO", compose.activity.getPreferences(0).getString("capture_mode", null))
            }
            // Opening settings keeps Help available when the user returns.
            compose.onNodeWithText(localized("找不到连页？", "Cannot find Lianye?")).assertIsDisplayed()
        } finally {
            instrumentation.removeMonitor(monitor)
            compose.runOnUiThread {
                repository.setCaptureMode(original)
                compose.activity.getPreferences(0).edit().putString("capture_mode", original.name).commit()
            }
        }
    }
}
