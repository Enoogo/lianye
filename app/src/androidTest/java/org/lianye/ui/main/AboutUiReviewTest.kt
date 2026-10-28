package org.lianye.ui.main

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.os.PatternMatcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.lianye.domain.model.CaptureMode
import org.lianye.ui.common.theme.LianyeTheme
import org.lianye.ui.main.components.AboutSheetContent
import java.io.File
import java.util.Locale

class AboutUiReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aboutAndUpdatesRemainReachableAcrossLanguagesThemesAndLargeFonts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val variant = mutableIntStateOf(0)
        val releasePage = IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addDataAuthority("github.com", null)
            addDataPath("/gxwane/lianye/releases", PatternMatcher.PATTERN_LITERAL)
        }
        val monitor = instrumentation.addMonitor(releasePage,
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            compose.setContent {
                key(variant.intValue) {
                    val english = variant.intValue % 2 == 1
                    val large = variant.intValue >= 2
                    val fontScale = if (large) 1.8f else 1f
                    val configuration = Configuration(context.resources.configuration).apply {
                        setLocales(LocaleList(if (english) Locale.US else Locale.SIMPLIFIED_CHINESE))
                        this.fontScale = fontScale
                    }
                    CompositionLocalProvider(
                        LocalContext provides context.createConfigurationContext(configuration),
                        LocalConfiguration provides configuration,
                        LocalDensity provides Density(LocalDensity.current.density, fontScale)
                    ) {
                        LianyeTheme(darkTheme = english) {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground) {
                                Box(Modifier.fillMaxSize()) {
                                    AboutSheetContent({}, {}, {},
                                        Modifier.widthIn(max = if (large) 320.dp else 560.dp)
                                            .fillMaxSize().testTag("help-review").padding(24.dp),
                                        captureMode = CaptureMode.MANUAL)
                                }
                            }
                        }
                    }
                }
            }
            for (index in 0..3) {
                compose.runOnIdle { variant.intValue = index }
                if (index >= 2) compose.onNodeWithTag("help-review").assertWidthIsEqualTo(320.dp)
                val english = index % 2 == 1
                val about = if (english) "About" else "关于"
                val updates = if (english) "View updates" else "查看更新"
                compose.onNodeWithText(if (english) "Privacy and about" else "隐私与关于").assertDoesNotExist()
                compose.onNodeWithText(about).performScrollTo().performClick()
                compose.onNodeWithText(if (english) "Lianye" else "连页").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(version).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(if (english) "Open source. No ads. Images stay on your device."
                    else "开源，无广告。截图仅在本机处理。")
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(updates).performScrollTo().assertIsDisplayed().performClick()
                compose.runOnIdle { assertEquals(index + 1, monitor.hits) }
                compose.onNodeWithText(updates).assertIsDisplayed()
                compose.waitForIdle()
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(500)
                val directory = File(context.getExternalFilesDir(null), "about-review").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    val name = listOf("chinese-light", "english-dark", "chinese-large", "english-dark-large")[index]
                    File(directory, "$name.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
