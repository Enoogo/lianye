package org.lianye.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.lianye.R
import org.lianye.domain.model.CaptureMode
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualControl
import org.lianye.domain.model.ManualGuide
import org.lianye.domain.model.ManualPhase
import org.lianye.ui.floating.ManualFloatingOverlayManager
import org.lianye.platform.DeviceVendor
import org.lianye.ui.common.theme.LianyeTheme
import org.lianye.ui.main.components.AboutBottomSheet
import org.lianye.ui.main.components.AutomaticCapturePreparationSheet
import org.lianye.ui.main.components.ManualCaptureControlSheet
import org.lianye.ui.main.components.ReplaceDraftDialog
import java.io.File
import java.util.Locale

/** Real Android renders plus reachability checks; callbacks never grant permissions or replace a draft. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class UiVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupHelpAndReplacementActionsRemainReachable() {
        val page = mutableIntStateOf(0)
        val mode = mutableStateOf(CaptureMode.AUTO)
        var overlayClicks = 0
        var cancelClicks = 0
        compose.setContent {
            ReviewLocale(Locale.SIMPLIFIED_CHINESE) {
                LianyeTheme {
                    MainScreen(
                        MainUiState(false, false, true, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_huawei),
                        captureMode = mode.value, onSelectCaptureMode = { mode.value = it },
                        onPrimaryAction = { page.intValue = if (mode.value == CaptureMode.AUTO) 2 else 4 }, onResumeDraft = {}, onNewCapture = {},
                        onHideOverlay = {}, onStopService = {}, onOpenAppDetailsSettings = {},
                        onOpenBatteryOptimization = {}, onCopyAdbCommand = {}
                    )
                    when (page.intValue) {
                        1 -> AboutBottomSheet(onDismissRequest = { page.intValue = 0 },
                            onOpenAppDetailsSettings = {}, onOpenBatteryOptimization = {}, onCopyAdbCommand = {},
                            captureMode = mode.value)
                        2, 3 -> AutomaticCapturePreparationSheet(needsProjection = page.intValue == 2,
                            vendor = DeviceVendor.HUAWEI, onDismissRequest = { page.intValue = 0 },
                            onConfirm = { page.intValue = 3 })
                        4 -> ManualCaptureControlSheet(onDismissRequest = { page.intValue = 0 },
                            onEnableOverlay = { overlayClicks++ })
                        5 -> ReplaceDraftDialog(onDismiss = { cancelClicks++; page.intValue = 0 }, onSave = {}, onDiscard = {})
                    }
                }
            }
        }
        screenshot("home-light")
        compose.runOnIdle { page.intValue = 1 }
        screenshot("help-auto")
        compose.onNodeWithText("找不到连页？").performClick()
        screenshot("help-path")
        compose.runOnIdle { page.intValue = 0 }
        compose.onNodeWithText("开始长截图").performClick()
        screenshot("prepare-projection")
        compose.onNodeWithText("继续授权").performClick()
        screenshot("prepare-auto")
        compose.runOnIdle { page.intValue = 0 }
        compose.onNodeWithText("自己滑动").performClick()
        screenshot("home-manual")
        compose.runOnIdle { page.intValue = 1 }
        screenshot("help-manual")
        compose.runOnIdle { page.intValue = 0 }
        compose.onNodeWithText("开始长截图").performClick()
        screenshot("prepare-manual")
        compose.onNodeWithText("使用通知栏").assertDoesNotExist()
        compose.onNodeWithText("前往设置").assertIsDisplayed().performClick()
        assertEquals(1, overlayClicks)
        compose.runOnIdle { page.intValue = 5 }
        compose.onNodeWithText("保存并新建").assertIsDisplayed()
        compose.onNodeWithText("放弃并新建").assertIsDisplayed()
        screenshot("replace-draft")
        compose.onNodeWithText("取消").performClick()
        assertEquals(1, cancelClicks)
        compose.onNodeWithText("开始长截图").assertIsDisplayed()
    }

    @Test fun largeEnglishReplacementKeepsAllChoicesVisibleAndCancelDoesNotSave() {
        val showing = mutableStateOf(true)
        var saves = 0
        var discards = 0
        compose.setContent {
            ReviewLocale(Locale.US, 1.8f) {
                LianyeTheme {
                    Box(Modifier.fillMaxSize()) {
                        if (showing.value) ReplaceDraftDialog(
                            onDismiss = { showing.value = false }, onSave = { saves++ }, onDiscard = { discards++ })
                    }
                }
            }
        }
        screenshot("replace-large-english")
        compose.onNodeWithText("Save and start").assertIsDisplayed()
        compose.onNodeWithText("Discard and start").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, saves)
        assertEquals(0, discards)
        compose.onNodeWithText("Current image is not saved").assertDoesNotExist()
    }

    @Test fun homeModesRemainReachableAcrossDarkAndLargeFontLayouts() {
        val mode = mutableStateOf(CaptureMode.AUTO)
        val variant = mutableIntStateOf(0)
        var starts = 0
        data class Layout(val locale: Locale, val fontScale: Float, val dark: Boolean, val inset: Int, val image: String)
        val layouts = listOf(
            Layout(Locale.SIMPLIFIED_CHINESE, 1f, true, 0, "home-dark-chinese"),
            Layout(Locale.SIMPLIFIED_CHINESE, 1.8f, false, 20, "home-large-chinese"),
            Layout(Locale.US, 1.8f, true, 20, "home-dark-large-english")
        )
        compose.setContent {
            key(variant.intValue) {
                val layout = layouts[variant.intValue]
                ReviewLocale(layout.locale, layout.fontScale) {
                    LianyeTheme(darkTheme = layout.dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = layout.inset.dp)) {
                            MainScreen(
                                MainUiState(false, false, true, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_huawei),
                                captureMode = mode.value, onSelectCaptureMode = { mode.value = it },
                                onPrimaryAction = { starts++ }, onResumeDraft = {}, onNewCapture = {},
                                onHideOverlay = {}, onStopService = {}, onOpenAppDetailsSettings = {},
                                onOpenBatteryOptimization = {}, onCopyAdbCommand = {}
                            )
                        }
                    }
                }
            }
        }
        for ((index, layout) in layouts.withIndex()) {
            compose.runOnIdle { variant.intValue = index; mode.value = CaptureMode.AUTO }
            val english = layout.locale == Locale.US
            compose.onNodeWithText(if (english) "Auto" else "自动滚动").assertIsDisplayed()
            if (index != 0) {
                compose.onNodeWithText(if (english) "Manual" else "自己滑动").assertIsDisplayed().performClick()
                assertEquals(CaptureMode.MANUAL, mode.value)
            } else {
                compose.onNodeWithText("自己滑动").assertIsDisplayed()
            }
            compose.onNodeWithText(if (english) "Start capture" else "开始长截图").assertIsDisplayed().performClick()
            assertEquals(index + 1, starts)
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            captureImage(layout.image)
        }
    }

    @Composable private fun ReviewLocale(locale: Locale, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale)); this.fontScale = fontScale
        }
        CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration),
            LocalConfiguration provides configuration,
            LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content)
    }

    @Test fun manualOverlayClearsItsOwnWindowsAfterGuidanceAndFinish() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(android.provider.Settings.canDrawOverlays(context))
        compose.setContent {
            LianyeTheme {
                MainScreen(MainUiState(false, false, true, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_huawei),
                    onPrimaryAction = {}, onResumeDraft = {}, onNewCapture = {}, onHideOverlay = {}, onStopService = {},
                    onOpenAppDetailsSettings = {}, onOpenBatteryOptimization = {}, onCopyAdbCommand = {})
            }
        }
        lateinit var overlay: ManualFloatingOverlayManager
        var unavailable = false
        compose.runOnUiThread {
            overlay = ManualFloatingOverlayManager(context, context.getSystemService(android.view.WindowManager::class.java),
                org.lianye.App.instance.appComponent.lianyeRepository,
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate),
                onStart = {}, onFinish = {}, onUnavailable = { unavailable = true })
            overlay.setProductVisible(false)
        }
        try {
            val ready = ManualCaptureState(phase = ManualPhase.READY, control = ManualControl.OVERLAY)
            val recording = ready.copy(phase = ManualPhase.RECORDING, acceptedFrames = 1, guide = ManualGuide.FULL)
            for ((name, state) in listOf("floating-ready" to ready, "floating-guide" to recording,
                "floating-route" to recording.copy(acceptedFrames = 2, guide = ManualGuide.ROUTE))) {
                compose.runOnUiThread { overlay.render(state) }
                instrumentation.waitForIdleSync()
                assertTrue(overlay.hasAttachedWindows)
                screenshot(name)
            }
            compose.runOnUiThread { overlay.render(recording.copy(guide = ManualGuide.RECOVERY, message = "temporarily_unmatched")) }
            instrumentation.waitForIdleSync()
            compose.waitUntil(5_000) {
                overlay.controlBounds?.let { it.height() > 200 && it.left >= 0 && it.right <= context.resources.displayMetrics.widthPixels } == true
            }
            screenshot("floating-unmatched")
            compose.runOnUiThread { overlay.render(ManualCaptureState()) }
            assertFalse(overlay.hasAttachedWindows)
            assertFalse(unavailable)
        } finally { compose.runOnUiThread { overlay.hide() } }
    }

    private fun screenshot(name: String) {
        val marker = when (name) {
            "home-light", "home-manual", "floating-ready", "floating-guide", "floating-route", "floating-unmatched" -> "开始长截图"
            "help-auto", "help-path", "help-manual" -> "帮助与设置"
            "prepare-projection" -> "允许屏幕捕获"
            "prepare-auto" -> "开启自动截图"
            "prepare-manual" -> "允许悬浮球"
            "replace-large-english" -> "Save and start"
            else -> "当前长图未保存"
        }
        var previous: androidx.compose.ui.geometry.Rect? = null
        var stable = 0
        compose.waitUntil(10_000) {
            val bounds = runCatching {
                compose.onNodeWithText(marker).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }.getOrNull()
            stable = if (bounds != null && bounds == previous) stable + 1 else 0
            previous = bounds
            stable >= 3
        }
        compose.waitForIdle()
        captureImage(name)
    }

    private fun captureImage(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Material ripples may finish on the Android render thread, beyond Compose's test clock.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-polish").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
