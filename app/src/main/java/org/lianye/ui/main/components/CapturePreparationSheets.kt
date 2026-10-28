package org.lianye.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.platform.DeviceVendor
import org.lianye.platform.DeviceVendorDetector
import org.lianye.platform.AccessibilityHelpState
import org.lianye.platform.AccessibilityHelpStatus
import org.lianye.ui.common.uiText
import org.lianye.ui.common.LianyePrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomaticCapturePreparationSheet(
    needsProjection: Boolean = false,
    vendor: DeviceVendor = DeviceVendorDetector.currentVendor,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    accessibilityHelpState: AccessibilityHelpState = AccessibilityHelpState()
) {
    val restricted = !needsProjection && accessibilityHelpState.status == AccessibilityHelpStatus.RESTRICTED
    val disconnected = !needsProjection && accessibilityHelpState.status == AccessibilityHelpStatus.NOT_CONNECTED
    ModalBottomSheet(onDismissRequest = onDismissRequest, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (needsProjection) uiText("允许屏幕捕获", "Allow screen capture")
                else if (restricted) uiText("无障碍受限制", "Accessibility is restricted")
                else uiText("开启自动截图", "Enable automatic capture"),
                style = MaterialTheme.typography.titleLarge)
            Text(
                if (needsProjection) uiText("Android 10 需要本次屏幕捕获授权，才能截取画面。", "Android 10 needs screen capture authorization for this session.")
                else if (restricted) uiText("先在连页的应用信息中允许受限制的设置，再返回开启无障碍。", "Allow restricted settings in app information for Lianye, then return to enable accessibility.")
                else if (disconnected) uiText("开关已开启，服务尚未连接。请在设置中关闭连页，再重新开启。", "The switch is on but the service is not connected. Turn Lianye off in Settings, then on again.")
                else uiText("开启无障碍后自动滚动，图片在本机处理。", "Enable accessibility to scroll automatically. Images stay on this device."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!needsProjection && !restricted && !disconnected) Text(stringResource(DeviceVendorDetector.getPreFlightHintRes(vendor)),
                style = MaterialTheme.typography.bodyMedium)
            LianyePrimaryButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(if (needsProjection) uiText("继续授权", "Continue")
                    else if (restricted) stringResource(R.string.btn_open_app_details)
                    else stringResource(R.string.preflight_btn_confirm))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualCaptureControlSheet(
    onDismissRequest: () -> Unit,
    onEnableOverlay: () -> Unit,
    isBusy: Boolean = false
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(uiText("允许悬浮球", "Allow floating control"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("用悬浮球开始、结束，并查看滑动提示。", "Use the floating control to start, finish and see swipe guidance."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LianyePrimaryButton(onClick = onEnableOverlay, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.preflight_btn_confirm))
            }
        }
    }
}
