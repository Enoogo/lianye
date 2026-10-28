package org.lianye.ui.main

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.lianye.App
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualControl
import org.lianye.domain.model.ManualGuide
import org.lianye.domain.model.ManualPhase
import org.lianye.ui.floating.FloatingOverlayManager
import org.lianye.ui.floating.ManualFloatingOverlayManager
import org.lianye.ui.common.localized

class FloatingCaptureVisibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recoveryGuidePersistsUntilConfirmedRecoveryAndIsHiddenInCleanCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        org.junit.Assume.assumeTrue(android.provider.Settings.canDrawOverlays(context))
        compose.runOnUiThread {
            compose.activity.setContent { Box(Modifier.fillMaxSize().background(Color.Magenta)) }
        }
        compose.waitForIdle()
        // Wait for the presented test region, including any transient OEM system banner.
        // A clear center alone can leave an export-cleanup notification in the baseline.
        val captureInset = (100 * context.resources.displayMetrics.density).toInt()
        var presented: android.graphics.Bitmap? = null
        compose.waitUntil(10_000) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val pixels = IntArray(screenshot.width * (screenshot.height - 2 * captureInset))
            screenshot.getPixels(pixels, 0, screenshot.width, 0, captureInset,
                screenshot.width, screenshot.height - 2 * captureInset)
            if (pixels.all { it == android.graphics.Color.MAGENTA }) {
                presented = screenshot
                true
            } else { screenshot.recycle(); false }
        }
        val baseline = requireNotNull(presented)
        val evidence = java.io.File(context.getExternalFilesDir(null), "ui-polish").apply { mkdirs() }
        java.io.File(evidence, "recovery-baseline.png").outputStream().use {
            baseline.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val overlay = ManualFloatingOverlayManager(context, context.getSystemService(WindowManager::class.java),
            App.instance.appComponent.lianyeRepository, scope, onStart = {}, onFinish = {}, onUnavailable = {})
        val failed = ManualCaptureState(phase = ManualPhase.RECORDING, acceptedFrames = 2,
            control = ManualControl.OVERLAY, guide = ManualGuide.RECOVERY, message = "temporarily_unmatched")
        fun guideView(): View? {
            val window = overlay.javaClass.getDeclaredField("guideWindow").apply { isAccessible = true }.get(overlay) ?: return null
            return window.javaClass.getDeclaredField("view").apply { isAccessible = true }.get(window) as View
        }
        try {
            instrumentation.runOnMainSync { overlay.setProductVisible(false); overlay.render(failed) }
            instrumentation.waitForIdleSync()
            val guide = requireNotNull(guideView())
            assertEquals(localized("向下滑回一点，松手稍停", "Swipe down a little, then release and pause"), guide.contentDescription)
            assertTrue((guide.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            instrumentation.runOnMainSync { overlay.render(failed) }
            assertSame("A repeated failed sample keeps the corrective guide visible", guide, guideView())
            runBlocking { overlay.hideBeforeCapture() }
            assertEquals(0f, guide.alpha)
            val hidden = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(evidence, "recovery-hidden.png").outputStream().use {
                hidden.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            var changed = 0
            // Include both the guide and floating notice; exclude system bars.
            for (y in captureInset until hidden.height - captureInset)
                for (x in 0 until hidden.width)
                    if (hidden.getPixel(x, y) != baseline.getPixel(x, y)) changed++
            hidden.recycle()
            assertEquals("Neither the recovery route nor the notice may leak into clean pixels", 0, changed)
            instrumentation.runOnMainSync {
                overlay.showAfterCapture()
                overlay.render(failed.copy(guide = ManualGuide.FULL, message = null))
            }
            assertEquals(localized("从这里向上滑，松手稍停", "Swipe up here, then release and pause"), requireNotNull(guideView()).contentDescription)
            instrumentation.runOnMainSync { overlay.render(failed.copy(phase = ManualPhase.FINISHING, guide = ManualGuide.HIDDEN)) }
            assertNull(guideView())
        } finally {
            baseline.recycle()
            instrumentation.runOnMainSync { overlay.hide() }
            scope.cancel()
        }
    }

    @Test fun cleanCaptureKeepsFinishHitTargetWhileItsPixelsAreTransparent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = compose.activity
        compose.runOnUiThread {
            activity.setContent { Box(Modifier.fillMaxSize().background(Color.Magenta)) }
        }
        compose.waitForIdle()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var finishes = 0
        // Production attaches the bubble from a Service, without an Activity's accelerated window.
        val overlayContext = instrumentation.targetContext.applicationContext
        val manager = FloatingOverlayManager(overlayContext, overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
            App.instance.appComponent.lianyeRepository, scope, onStartCapture = {}, onStopCapture = { finishes++ }, onOpenPreview = {},
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        try {
            instrumentation.runOnMainSync {
                manager.updateManualState(ManualCaptureState(phase = ManualPhase.RECORDING, acceptedFrames = 1))
                manager.show()
            }
            compose.waitUntil(5_000) { manager.controlBounds?.let { it.width() > 200 && it.height() > 80 } == true }
            val visible = instrumentation.uiAutomation.takeScreenshot()
            val boundsBefore = requireNotNull(manager.controlBounds)
            assertNotEquals(android.graphics.Color.MAGENTA, visible.getPixel(boundsBefore.centerX(), boundsBefore.centerY()))
            visible.recycle()
            assertNotNull("The transparent window must actually submit frames", runBlocking { manager.hideBeforeCapture() })
            val field = manager.javaClass.getDeclaredField("touchLayout").apply { isAccessible = true }
            val view = field.get(manager) as View
            assertEquals(View.VISIBLE, view.visibility)
            assertEquals(0f, view.alpha)
            assertEquals(1f, (view.layoutParams as WindowManager.LayoutParams).alpha)
            val softwareFrame = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            instrumentation.runOnMainSync { view.draw(android.graphics.Canvas(softwareFrame)) }
            val softwarePixels = IntArray(softwareFrame.width * softwareFrame.height)
            softwareFrame.getPixels(softwarePixels, 0, softwareFrame.width, 0, 0, softwareFrame.width, softwareFrame.height)
            softwareFrame.recycle()
            assertEquals("Software root drawing must also be transparent; hardware=${view.isHardwareAccelerated}",
                0, softwarePixels.count { it ushr 24 != 0 })
            val bounds = requireNotNull(manager.controlBounds)
            val hidden = instrumentation.uiAutomation.takeScreenshot()
            var unexpectedPixels = 0
            for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(hidden.height)) {
                for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(hidden.width)) {
                    if (hidden.getPixel(x, y) != android.graphics.Color.MAGENTA) unexpectedPixels++
                }
            }
            hidden.recycle()
            assertEquals("Hidden overlay must emit no pixels; hardware=${view.isHardwareAccelerated}", 0, unexpectedPixels)
            val x = bounds.right - 28 * activity.resources.displayMetrics.density
            val y = bounds.exactCenterY()
            val time = SystemClock.uptimeMillis()
            instrumentation.sendPointerSync(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
            instrumentation.sendPointerSync(MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, x, y, 0))
            instrumentation.waitForIdleSync()
            assertEquals("Finish must receive the tap inside $bounds", 1, finishes)
        } finally {
            instrumentation.runOnMainSync { manager.hide() }
            scope.cancel()
        }
    }
}
