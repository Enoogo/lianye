package org.lianye.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.lianye.App
import org.lianye.R
import org.lianye.data.draft.DraftStore
import org.lianye.domain.model.CaptureCompletion
import org.lianye.engine.model.TileMetadata
import org.lianye.ui.common.localized
import java.io.DataOutputStream
import java.io.File

class MainActivityDraftTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = App.instance.appComponent.lianyeRepository

    @Test
    fun savedCaptureReturnsToFreshHome() {
        val preferences = compose.activity.getPreferences(android.content.Context.MODE_PRIVATE)
        val previousMode = preferences.getString("capture_mode", "AUTO")
        try {
            preferences.edit().putString("capture_mode", "AUTO").commit()
            repository.setCaptureMode(org.lianye.domain.model.CaptureMode.AUTO)
            assertTrue(repository.discardDraft())
            val file = File(App.instance.appComponent.draftStore.tilesDirectory, "saved-home-qa.raw")
            file.parentFile!!.mkdirs()
            DataOutputStream(file.outputStream().buffered()).use { output ->
                repeat(360 * 720) { output.writeInt(Color.WHITE) }
            }
            repository.finishWeaving(listOf(TileMetadata(0, file, 360, 720, 0)), CaptureCompletion.USER_STOPPED)
            val captured = requireNotNull(repository.draft.value)
            repository.markDraftSaved(captured.revision)
            assertTrue(repository.draft.value!!.isSaved)
            repository.persistCurrentDraft()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(context.getString(R.string.home_continue_draft)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.home_enable)).assertIsDisplayed()
            compose.runOnUiThread {
                compose.activity.startActivity(Intent(compose.activity.intent).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_draft", true)
                })
            }
            compose.onNodeWithText(localized("长图", "Long image")).assertIsDisplayed()
            compose.runOnUiThread {
                compose.activity.startActivity(Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
            compose.onNodeWithText(context.getString(R.string.home_enable)).assertIsDisplayed().performClick()
            compose.onNodeWithText(localized("当前长图未保存", "Current image is not saved")).assertDoesNotExist()
            compose.waitUntil(5_000) { repository.draft.value == null }
            val preparationTitle = if (android.os.Build.VERSION.SDK_INT == 29)
                localized("允许屏幕捕获", "Allow screen capture") else localized("开启自动截图", "Enable automatic capture")
            // Draft removal precedes the sheet's animated presentation on a real device.
            compose.waitUntil(5_000) {
                runCatching { compose.onNodeWithText(preparationTitle).assertIsDisplayed(); true }.getOrDefault(false)
            }
            compose.onNodeWithText(preparationTitle).assertIsDisplayed()
        } finally {
            preferences.edit().putString("capture_mode", previousMode).commit()
            assertTrue(repository.discardDraft())
        }
    }

    @Test
    fun appliedCropAndDraftSurviveActivityRecreationAndHomeNavigation() {
        assertTrue(repository.discardDraft())
        val tiles = App.instance.appComponent.draftStore.tilesDirectory
        tiles.mkdirs()
        val file = File(tiles, "activity-qa.raw")
        val width = 360
        val height = 1800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 248, 243))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(36, 39, 34); textSize = 26f }
        canvas.drawText("Offline long capture", 24f, 56f, paint)
        repeat(12) { index ->
            val top = 100f + index * 140
            paint.color = Color.rgb(229, 236, 248)
            canvas.drawRoundRect(24f, top, 336f, top + 100, 12f, 12f, paint)
            paint.color = Color.rgb(54, 91, 157)
            paint.textSize = 18f
            canvas.drawText("Section ${index + 1}", 40f, top + 32, paint)
            paint.color = Color.rgb(84, 89, 80)
            paint.textSize = 14f
            canvas.drawText("Keep the content, keep it local.", 40f, top + 64, paint)
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        DataOutputStream(file.outputStream().buffered()).use { output -> pixels.forEach(output::writeInt) }
        repository.finishWeaving(listOf(TileMetadata(0, file, width, height, 0)), CaptureCompletion.USER_STOPPED)
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(compose.activity.intent).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_draft", true)
            })
        }
        compose.onNodeWithText(localized("长图", "Long image")).assertIsDisplayed()
        screenshot("preview-light")
        compose.onNodeWithText(localized("裁剪", "Crop")).performClick()
        screenshot("crop-light")
        compose.onNodeWithContentDescription(localized("左上裁剪角", "Top left crop corner")).performTouchInput {
            swipe(center, center + Offset(50f, 60f))
        }
        compose.onNodeWithText(localized("应用", "Apply")).performClick()
        compose.waitForIdle()
        val applied = repository.draft.value!!.edits
        assertTrue(applied.crop!!.left > 0 && applied.crop.top > 0)
        assertEquals(0, applied.masks.size)
        repository.persistCurrentDraft()
        val reopened = DraftStore(File(context.filesDir, "drafts/current")).load()!!
        assertEquals(applied, reopened.edits)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(localized("长图", "Long image")).assertIsDisplayed()
        screenshot("preview-cropped")
        compose.onNodeWithContentDescription(localized("返回，保留草稿", "Back, keeping the draft")).performClick()
        compose.onNodeWithText(context.getString(R.string.home_continue_draft)).assertIsDisplayed()
        screenshot("home-with-draft")
        compose.onNodeWithText(context.getString(R.string.home_continue_draft)).performClick()
        assertEquals(applied, repository.draft.value!!.edits)
        compose.onNodeWithText(localized("裁剪", "Crop")).performClick()
        compose.onNodeWithText(localized("还原", "Reset")).performClick()
        compose.onNodeWithText(localized("取消", "Cancel")).performClick()
        assertEquals(applied, repository.draft.value!!.edits)
        repository.persistCurrentDraft()
    }

    private fun screenshot(name: String) {
        if (name.startsWith("preview") || name == "home-with-draft") {
            val loaded = if (name.startsWith("preview")) localized("图片已加载", "Image loaded")
                else localized("缩略图已加载", "Thumbnail loaded")
            compose.waitUntil(10_000) {
                compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, loaded)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.waitForIdle()
        // The loaded semantics can precede the Android surface presenting the decoded image.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        val directory = File(context.getExternalFilesDir(null), "ui-qa").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
