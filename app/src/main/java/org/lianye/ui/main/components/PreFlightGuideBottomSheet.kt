package org.lianye.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.platform.DeviceVendor
import org.lianye.platform.DeviceVendorDetector
import org.lianye.ui.common.uiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreFlightGuideBottomSheet(
    vendor: DeviceVendor = DeviceVendorDetector.currentVendor,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    needsProjection: Boolean = false,
    needsAccessibility: Boolean = true,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val path = DeviceVendorDetector.getSettingsPathRes(vendor)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(if (needsProjection) uiText("允许屏幕捕获", "Allow screen capture") else stringResource(R.string.preflight_guide_title), style = MaterialTheme.typography.headlineSmall)
            Text(if (needsProjection) {
                if (needsAccessibility) uiText("Android 10 需要通过系统屏幕捕获授权读取截图。随后还需要开启无障碍服务，用于滚动页面和显示截图按钮。", "Android 10 requires system screen capture permission to read screenshots. Next, enable the accessibility service to scroll pages and display capture controls.")
                else uiText("Android 10 需要通过系统屏幕捕获授权读取截图。无障碍服务已开启，授权后即可继续截图。", "Android 10 requires system screen capture permission to read screenshots. Accessibility is already enabled; allow capture to continue.")
            } else stringResource(R.string.preflight_permission_use), style = MaterialTheme.typography.bodyLarge)
            if (!needsProjection) {
                Text(stringResource(R.string.preflight_guide_desc), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (needsProjection) {
                            if (needsAccessibility) uiText("1. 点击下方按钮\n2. 在系统窗口中确认允许屏幕捕获\n3. 返回这里继续开启无障碍服务", "1. Tap the button below\n2. Allow screen capture in the system dialog\n3. Return here to enable accessibility")
                            else uiText("1. 点击下方按钮\n2. 在系统窗口中确认允许屏幕捕获\n3. 切换到要截图的页面，点击悬浮按钮", "1. Tap the button below\n2. Allow screen capture in the system dialog\n3. Open the page you want to capture and tap the floating button")
                        } else stringResource(path, stringResource(R.string.app_name)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (needsProjection) uiText("授权取消或失效后，可以从首页重新开启。", "If permission is declined or ends, enable it again from Home.") else stringResource(R.string.preflight_path_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                stringResource(R.string.preflight_privacy_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text(if (needsProjection) uiText("继续授权", "Continue") else stringResource(R.string.preflight_btn_confirm)) }
        }
    }
}
