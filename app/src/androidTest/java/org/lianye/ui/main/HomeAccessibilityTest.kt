package org.lianye.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.lianye.R
import org.lianye.ui.common.theme.LianyeTheme
import java.io.File
import java.util.Locale

class HomeAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun primaryActionRemainsReachableInDarkChineseAndLargeEnglish() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val variant = mutableIntStateOf(0)
        var clicks = 0
        compose.setContent {
            val english = variant.intValue == 1
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(if (english) Locale.US else Locale.SIMPLIFIED_CHINESE))
                fontScale = if (english) 1.5f else 1f
            }
            val localContext = context.createConfigurationContext(configuration)
            val density = LocalDensity.current.density
            key(variant.intValue) {
                CompositionLocalProvider(LocalContext provides localContext, LocalConfiguration provides configuration,
                    LocalDensity provides Density(density, configuration.fontScale)) {
                    LianyeTheme(darkTheme = !english) {
                        MainScreen(
                            uiState = MainUiState(false, false, true, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_aosp),
                            onPrimaryAction = { clicks++ }, onResumeDraft = {}, onNewCapture = {},
                            onHideOverlay = {}, onStopService = {}, onOpenAppDetailsSettings = {},
                            onOpenBatteryOptimization = {}, onCopyAdbCommand = {}
                        )
                    }
                }
            }
        }
        val chinese = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.SIMPLIFIED_CHINESE))
        })
        compose.onNodeWithText("开始长截图").assertIsDisplayed().performClick()
        screenshot("home-dark-zh")
        compose.runOnIdle { variant.intValue = 1 }
        screenshot("home-large-en-top")
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.US))
        })
        compose.onNodeWithText("Start capture").assertIsDisplayed().performClick()
        screenshot("home-large-en-action")
        assertEquals(2, clicks)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-qa").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
