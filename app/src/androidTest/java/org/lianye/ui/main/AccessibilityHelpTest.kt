package org.lianye.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.lianye.R
import org.lianye.domain.model.CaptureMode
import org.lianye.platform.AccessibilityHelpState
import org.lianye.platform.AccessibilityHelpStatus
import org.lianye.platform.DeviceVendor
import org.lianye.platform.DeviceVendorDetector
import org.lianye.platform.RestrictionStatus
import org.lianye.ui.common.theme.LianyeTheme
import org.lianye.ui.main.components.AboutSheetContent
import org.lianye.ui.main.components.AutomaticCapturePreparationSheet
import java.io.File
import java.util.Locale

/** ROMs are injected for content checks; only the attached Huawei is a real device verification. */
class AccessibilityHelpTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deviceIdentificationShowsOneRelevantGuideWithoutADevicePicker() {
        data class Device(val maker: String?, val brand: String, val name: String, val list: String, val file: String)
        val devices = listOf(
            Device("Xiaomi", "POCO", "小米 / Redmi / POCO", "通用 → 已下载的应用", "xiaomi"),
            Device("OPPO", "OPPO", "OPPO", "已下载的应用", "oppo"),
            Device("OPPO", "OnePlus", "一加", "已下载的应用", "oneplus"),
            Device("OPPO", "realme", "realme", "已下载的应用", "realme"),
            Device("vivo", "iQOO", "vivo / iQOO", "已下载的应用", "vivo"),
            Device("Huawei", "Huawei", "华为", "已安装的服务", "huawei"),
            Device("Huawei", "HONOR", "荣耀", "已安装的服务", "honor"),
            Device(null, "Samsung", "三星", "已安装的应用程序", "samsung"),
            Device("unknown", "unknown", "Android 设备", "已下载的应用", "android")
        )
        val selected = mutableIntStateOf(0)
        compose.setContent {
            key(selected.intValue) {
                val device = devices[selected.intValue]
                Review {
                    AboutSheetContent({}, {}, {}, Modifier.fillMaxSize().padding(24.dp),
                        accessibilityHelpState = AccessibilityHelpState(),
                        vendor = DeviceVendorDetector.resolveVendor(device.maker, device.brand), androidRelease = "10")
                }
            }
        }
        for ((index, device) in devices.withIndex()) {
            compose.runOnIdle { selected.intValue = index }
            compose.onNodeWithText("${device.name} · Android 10").assertIsDisplayed()
            compose.onNodeWithText("找不到连页？").performScrollTo().performClick()
            compose.onNodeWithText("打开「${device.list}」").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("选择「连页」，开启服务").assertIsDisplayed()
            compose.onNodeWithText("选择系统").assertDoesNotExist()
            compose.onNodeWithText("选择品牌").assertDoesNotExist()
            screenshot("guide-${device.file}")
        }
    }

    @Test fun permissionChangesReplaceActionsWithoutClaimingUnknownRestrictionsAreAllowed() {
        val state = mutableStateOf(AccessibilityHelpState.resolve(false, false, RestrictionStatus.UNKNOWN, 35))
        var settings = 0
        var appInfo = 0
        var manual = 0
        compose.setContent {
            Review {
                AboutSheetContent({ appInfo++ }, {}, {}, Modifier.fillMaxSize().padding(24.dp),
                    onOpenAccessibilitySettings = { settings++ }, accessibilityHelpState = state.value,
                    onUseManualCapture = { manual++ }, vendor = DeviceVendor.HONOR, androidRelease = "15")
            }
        }
        compose.onNodeWithText("未开启").assertIsDisplayed()
        compose.onNodeWithText("已解除限制").assertDoesNotExist()
        compose.onNodeWithText("前往设置").performClick()
        assertEquals(1, settings)
        compose.onNodeWithText("开关无法开启？").performScrollTo().performClick()
        compose.onNodeWithText("打开应用信息").performScrollTo().performClick()
        assertEquals(1, appInfo)
        screenshot("unknown-restriction")

        compose.runOnIdle { state.value = AccessibilityHelpState.resolve(false, false, RestrictionStatus.BLOCKED, 35) }
        compose.onNodeWithText("受限制").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("开关无法开启？").assertDoesNotExist()
        compose.onNodeWithText("打开应用信息").performScrollTo().performClick()
        assertEquals(2, appInfo)
        compose.onNodeWithText("返回无障碍设置").performScrollTo().performClick()
        assertEquals(2, settings)
        screenshot("restricted-honor")

        compose.runOnIdle { state.value = AccessibilityHelpState.resolve(true, true, RestrictionStatus.BLOCKED, 35) }
        compose.onNodeWithText("已开启").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("找不到连页？").assertDoesNotExist()
        compose.onNodeWithText("开关无法开启？").assertDoesNotExist()
        compose.onNodeWithText("打开应用信息").assertDoesNotExist()
        compose.onNodeWithText("使用自己滑动").assertDoesNotExist()
        compose.onNodeWithText("管理无障碍").performScrollTo().performClick()
        assertEquals(3, settings)
        screenshot("connected")

        compose.runOnIdle { state.value = AccessibilityHelpState.resolve(false, true, RestrictionStatus.ALLOWED, 35) }
        compose.onNodeWithText("未连接").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("前往设置").performScrollTo().performClick()
        assertEquals(4, settings)
        compose.onNodeWithText("使用自己滑动").performScrollTo().performClick()
        assertEquals(1, manual)
        screenshot("enabled-not-connected")
    }

    @Test fun manualFallbackReturnsToTheExistingHomeModeWithoutRequestingAccessibility() {
        val mode = mutableStateOf(CaptureMode.AUTO)
        var settings = 0
        compose.setContent {
            Review {
                MainScreen(MainUiState(false, false, true, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_huawei),
                    captureMode = mode.value, onSelectCaptureMode = { mode.value = it },
                    onPrimaryAction = {}, onResumeDraft = {}, onNewCapture = {}, onHideOverlay = {}, onStopService = {},
                    onOpenAppDetailsSettings = {}, onOpenBatteryOptimization = {}, onCopyAdbCommand = {},
                    onOpenAccessibilitySettings = { settings++ }, accessibilityHelpState = AccessibilityHelpState())
            }
        }
        compose.onNodeWithText("帮助").performClick()
        compose.onNodeWithText("开关无法开启？").assertDoesNotExist()
        compose.onNodeWithText("使用自己滑动").performScrollTo().performClick()
        assertEquals(CaptureMode.MANUAL, mode.value)
        assertEquals(0, settings)
        compose.onNodeWithText("开始长截图").assertIsDisplayed()
        compose.onNodeWithText("帮助").performClick()
        compose.onNodeWithText("自己滑动无需开启无障碍。").assertIsDisplayed()
        compose.onNodeWithText("前往设置").assertDoesNotExist()
        compose.onNodeWithText("找不到连页？").assertDoesNotExist()
        screenshot("manual-no-accessibility")
    }

    @Test fun largeEnglishAndChineseHelpKeepActionsReachableInDarkAndNarrowLayouts() {
        val variant = mutableIntStateOf(0)
        var opened = 0
        var usedManual = 0
        compose.setContent {
            key(variant.intValue) {
                Review(english = variant.intValue == 0, large = true, dark = variant.intValue == 0) {
                    AboutSheetContent({ opened++ }, {}, {}, Modifier.fillMaxSize().padding(24.dp),
                        accessibilityHelpState = AccessibilityHelpState.resolve(false, false, RestrictionStatus.BLOCKED, 35),
                        onUseManualCapture = { usedManual++ }, vendor = DeviceVendor.SAMSUNG, androidRelease = "15")
                }
            }
        }
        for (index in 0..1) {
            compose.runOnIdle { variant.intValue = index }
            val english = index == 0
            val appInfo = if (english) "Open app information" else "打开应用信息"
            compose.onNodeWithText(appInfo).performScrollTo().assertIsDisplayed()
            screenshot(if (english) "large-dark-english" else "large-chinese")
            compose.onNodeWithText(appInfo).performClick()
            compose.onNodeWithText(if (english) "Use manual capture" else "使用自己滑动")
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals(index + 1, opened)
            assertEquals(index + 1, usedManual)
        }
    }

    @Test fun restrictedPreparationOffersAppInformationWhileProjectionStillTakesPriority() {
        val projection = mutableStateOf(false)
        var confirmed = 0
        compose.setContent {
            Review {
                AutomaticCapturePreparationSheet(needsProjection = projection.value,
                    onDismissRequest = {}, onConfirm = { confirmed++ },
                    accessibilityHelpState = AccessibilityHelpState.resolve(false, false, RestrictionStatus.BLOCKED, 35))
            }
        }
        compose.onNodeWithText("无障碍受限制").assertIsDisplayed()
        compose.onNodeWithText("前往设置").assertDoesNotExist()
        compose.onNodeWithText("打开应用信息").performClick()
        assertEquals(1, confirmed)
        screenshot("prepare-restricted")
        compose.runOnIdle { projection.value = true }
        compose.onNodeWithText("允许屏幕捕获").assertIsDisplayed()
        compose.onNodeWithText("打开应用信息").assertDoesNotExist()
        compose.onNodeWithText("继续授权").performClick()
        assertEquals(2, confirmed)
    }

    @Composable private fun Review(english: Boolean = false, large: Boolean = false, dark: Boolean = false,
        content: @Composable () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fontScale = if (large) 1.8f else 1f
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(if (english) Locale.US else Locale.SIMPLIFIED_CHINESE))
            this.fontScale = fontScale
        }
        CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration),
            LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            LianyeTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(Modifier.widthIn(max = if (large) 320.dp else 560.dp).fillMaxSize()) { content() }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "adaptive-help").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
