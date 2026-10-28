package org.lianye.ui.preview

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.lianye.domain.model.Draft
import org.lianye.engine.model.TileMetadata
import org.lianye.ui.common.theme.LianyeTheme
import java.io.File
import java.util.Locale

class PreviewRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pendingCornerCropSurvivesRestorationAndCancelKeepsDraft() {
        withRawDraft("restore-crop", 20, 30_001) { draft ->
            var edits = 0
            var saveSplit: Boolean? = null
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                English {
                    LianyeTheme {
                        ScrollPreviewScreen(
                            draft = draft, onDraftChange = { edits++ }, onSaveClick = { saveSplit = it },
                            onShareClick = {}, onBackClick = {}, onDoneClick = {}, onViewportChange = { _, _ -> }
                        )
                    }
                }
            }
            waitForImage()
            compose.onNodeWithText("Redact").assertDoesNotExist()
            compose.onNodeWithText("Done").assertDoesNotExist()
            compose.onNodeWithText("Crop").performClick()
            compose.onNodeWithContentDescription("Top left crop corner").performTouchInput {
                swipe(center, center + Offset(48f, 72f), durationMillis = 400)
            }
            compose.onNodeWithContentDescription("Undo crop").assertIsEnabled()
            assertEquals(0, edits)
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithContentDescription("Undo crop").assertIsEnabled()
            compose.onNodeWithText("Apply").assertIsEnabled()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(0, edits)
            compose.onNodeWithText("Save").performClick()
            assertEquals(true, saveSplit)
        }
    }

    @Test
    fun cropHistoryStaysLocalAndApplyCommitsOnlyOnce() {
        withRawDraft("apply-crop", 120, 160) { original ->
            var draft by mutableStateOf(original)
            var editCount = 0
            compose.setContent {
                English {
                    LianyeTheme {
                        ScrollPreviewScreen(
                            draft = draft,
                            onDraftChange = { draft = it; editCount++ },
                            onSaveClick = {}, onShareClick = {}, onBackClick = {},
                            onDoneClick = {}, onViewportChange = { _, _ -> }
                        )
                    }
                }
            }
            waitForImage()
            compose.onNodeWithText("Crop").performClick()
            compose.onNodeWithContentDescription("Top left crop corner").performTouchInput {
                swipe(center, center + Offset(50f, 70f), durationMillis = 400)
            }
            compose.onNodeWithContentDescription("Undo crop").assertIsEnabled().performClick()
            compose.onNodeWithContentDescription("Undo crop").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Redo crop").performClick()
            assertEquals(0, editCount)
            compose.onNodeWithText("Apply").performClick()
            assertEquals(1, editCount)
            assertTrue(draft.crop.left > original.crop.left)
            assertTrue(draft.crop.top > original.crop.top)
            assertEquals(original.crop.right, draft.crop.right)
            assertEquals(original.crop.bottom, draft.crop.bottom)
        }
    }

    private fun waitForImage() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Image loaded")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @androidx.compose.runtime.Composable
    private fun English(content: @androidx.compose.runtime.Composable () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.US)) }
        CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
    }

    private fun withRawDraft(id: String, width: Int, height: Int, action: (Draft) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "${id}.raw")
        val row = ByteArray(width * 4) { if (it % 4 == 0) 0xFF.toByte() else 0xAA.toByte() }
        file.outputStream().buffered().use { output -> repeat(height) { output.write(row) } }
        try { action(Draft(id, listOf(TileMetadata(0, file, width, height, 0)))) }
        finally { file.delete() }
    }
}
