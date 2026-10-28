package org.lianye.ui.main

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.lianye.R
import org.lianye.domain.model.CaptureMode
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualControl
import org.lianye.domain.model.ManualPhase
import org.lianye.ui.common.localized
import org.lianye.ui.common.theme.LianyeTheme
import org.lianye.ui.main.components.AutomaticCapturePreparationSheet
import org.lianye.ui.main.components.ManualCaptureControlSheet
import org.lianye.ui.main.components.AboutBottomSheet

/** Verify the product entry points rather than implementation details of a mode selector. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CaptureEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureModesAreDirectlyReachableFromHome() {
        val mode = mutableStateOf(CaptureMode.AUTO)
        var started: CaptureMode? = null
        compose.setContent {
            LianyeTheme {
                MainScreen(
                    MainUiState(false, false, true, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_huawei),
                    captureMode = mode.value,
                    onSelectCaptureMode = { mode.value = it },
                    onPrimaryAction = { started = mode.value }, onResumeDraft = {}, onNewCapture = {},
                    onHideOverlay = {}, onStopService = {}, onOpenAppDetailsSettings = {},
                    onOpenBatteryOptimization = {}, onCopyAdbCommand = {}
                )
            }
        }
        compose.onNodeWithText(localized("自动滚动", "Auto")).assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText(localized("自己滑动", "Manual")).assertIsDisplayed().performClick()
        compose.onNodeWithText(localized("自己滑动", "Manual")).assertIsSelected()
        compose.onNodeWithText(localized("自动滚动", "Auto")).assertIsNotSelected()
        compose.onNodeWithText(localized("开始长截图", "Start capture")).performClick()
        assertEquals(CaptureMode.MANUAL, started)
        compose.onNodeWithText(localized("自动滚动", "Auto")).performClick()
        assertEquals(CaptureMode.AUTO, mode.value)
    }

    @Test fun manualPermissionDoesNotAskUsersToChooseAControl() {
        compose.setContent {
            LianyeTheme {
                ManualCaptureControlSheet(onDismissRequest = {}, onEnableOverlay = {})
            }
        }
        compose.onNodeWithText(localized("使用通知栏", "Use notifications")).assertDoesNotExist()
    }

    @Test fun automaticPermissionDoesNotOfferAnotherCaptureMode() {
        compose.setContent {
            LianyeTheme {
                AutomaticCapturePreparationSheet(onDismissRequest = {}, onConfirm = {})
            }
        }
        compose.onNodeWithText(localized("手动截图", "Capture manually")).assertDoesNotExist()
    }

    @Test fun helpDoesNotDuplicateModeOrCaptureEntrances() {
        compose.setContent {
            LianyeTheme {
                AboutBottomSheet(onDismissRequest = {}, onOpenAppDetailsSettings = {},
                    onOpenBatteryOptimization = {}, onCopyAdbCommand = {}, captureMode = CaptureMode.MANUAL)
            }
        }
        compose.onNodeWithText(localized("自动滚动", "Auto")).assertDoesNotExist()
        compose.onNodeWithText(localized("自己滑动", "Manual")).assertDoesNotExist()
        compose.onNodeWithText(localized("开始手动截图", "Start manual capture")).assertDoesNotExist()
        compose.onNodeWithText(localized("自动截图设置", "Automatic capture setup")).assertDoesNotExist()
    }

    @Test fun activeCaptureLocksModeSelection() {
        compose.setContent {
            LianyeTheme {
                MainScreen(
                    MainUiState(true, false, false, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_huawei).copy(isCapturing = true),
                    captureMode = CaptureMode.MANUAL,
                    manualSession = ManualCaptureState(phase = ManualPhase.RECORDING, control = ManualControl.OVERLAY),
                    onPrimaryAction = {}, onResumeDraft = {}, onNewCapture = {},
                    onHideOverlay = {}, onStopService = {}, onOpenAppDetailsSettings = {},
                    onOpenBatteryOptimization = {}, onCopyAdbCommand = {}
                )
            }
        }
        compose.onNodeWithText(localized("自动滚动", "Auto")).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText(localized("自己滑动", "Manual")).assertIsDisplayed().assertIsNotEnabled()
    }
}
