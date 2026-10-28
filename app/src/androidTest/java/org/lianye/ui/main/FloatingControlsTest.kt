package org.lianye.ui.main

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.lianye.domain.model.*
import org.lianye.ui.common.localized
import org.lianye.ui.common.theme.LianyeTheme
import org.lianye.ui.floating.LianyeFloatingBubble

class FloatingControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedJoinKeepsReadableInstructionAndFinishUntilRecovery() {
        val state = mutableStateOf(ManualCaptureState(phase = ManualPhase.RECORDING, acceptedFrames = 1,
            control = ManualControl.OVERLAY, message = "temporarily_unmatched"))
        var finishes = 0
        compose.setContent {
            LianyeTheme {
                LianyeFloatingBubble(state = WeavingState.Idle(), manualSession = state.value,
                    onStartClick = {}, onStopClick = { finishes++ }, onPreviewClick = {})
            }
        }
        compose.onNodeWithText(localized("未接上", "Not joined")).assertIsDisplayed()
        compose.onNodeWithText(localized("向下滑回一点，稍停", "Swipe down a little; pause")).assertIsDisplayed()
        compose.onNodeWithText(localized("结束", "Finish")).assertIsDisplayed().performClick()
        assertEquals(1, finishes)
        compose.runOnIdle { state.value = state.value.copy(acceptedFrames = 2, message = null) }
        compose.onNodeWithText(localized("未接上", "Not joined")).assertDoesNotExist()
        compose.onNodeWithText(localized("结束", "Finish")).assertIsDisplayed()
    }

    @Test fun recoveryRouteKeepsFinishAndAvoidsRepeatingEndpointInstructions() {
        compose.setContent {
            LianyeTheme {
                LianyeFloatingBubble(state = WeavingState.Idle(),
                    manualSession = ManualCaptureState(phase = ManualPhase.RECORDING, acceptedFrames = 2,
                        control = ManualControl.OVERLAY, guide = ManualGuide.RECOVERY, message = "temporarily_unmatched"),
                    onStartClick = {}, onStopClick = {}, onPreviewClick = {})
            }
        }
        compose.onNodeWithText(localized("未接上", "Not joined")).assertIsDisplayed()
        compose.onNodeWithText(localized("结束", "Finish")).assertIsDisplayed()
        compose.onNodeWithText(localized("向下滑回一点，稍停", "Swipe down a little; pause")).assertDoesNotExist()
    }

    @Test fun manualFirstFrameAndCheckpointsNeverTurnFinishIntoStartOrPreview() {
        val phase = mutableStateOf(ManualPhase.READY)
        var starts = 0
        var finishes = 0
        var previews = 0
        compose.setContent {
            LianyeTheme {
                LianyeFloatingBubble(state = WeavingState.Idle(), hasDraft = true,
                    manualSession = ManualCaptureState(phase = phase.value, acceptedFrames = 1),
                    onStartClick = { starts++ }, onStopClick = { finishes++ }, onPreviewClick = { previews++ })
            }
        }
        compose.onNodeWithText(localized("开始截图", "Capture")).performClick()
        assertEquals(1, starts)
        for (active in listOf(ManualPhase.TAKING_FIRST, ManualPhase.RECORDING, ManualPhase.GAP)) {
            compose.runOnIdle { phase.value = active }
            compose.onNodeWithText(localized("结束", "Finish")).assertIsDisplayed().performClick()
            compose.onNodeWithText(localized("开始截图", "Capture")).assertDoesNotExist()
            compose.onNodeWithText(localized("继续编辑", "Continue draft")).assertDoesNotExist()
        }
        assertEquals(3, finishes)
        assertEquals(0, previews)
        compose.runOnIdle { phase.value = ManualPhase.FINISHING }
        compose.onNodeWithText(localized("结束", "Finish")).assertIsNotEnabled()
        compose.runOnIdle { phase.value = ManualPhase.FIRST_FAILED }
        compose.onNodeWithText(localized("重新截图", "Try again")).performClick()
        assertEquals(2, starts)
    }
}
