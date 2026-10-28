package org.lianye.ui.main.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.platform.AccessibilityHelpState
import org.lianye.platform.AccessibilityHelpStatus
import org.lianye.platform.DeviceVendor
import org.lianye.platform.DeviceVendorDetector
import org.lianye.ui.common.LianyePrimaryButton
import org.lianye.ui.common.uiText

@Composable
internal fun DeviceAccessibilityHelp(
    state: AccessibilityHelpState,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    enabled: Boolean,
    vendor: DeviceVendor = DeviceVendorDetector.currentVendor,
    androidRelease: String = Build.VERSION.RELEASE,
    onUseManualCapture: (() -> Unit)? = null
) {
    val connected = state.status == AccessibilityHelpStatus.CONNECTED
    val blocked = state.status == AccessibilityHelpStatus.RESTRICTED
    val disconnected = state.status == AccessibilityHelpStatus.NOT_CONNECTED
    val status = when (state.status) {
        AccessibilityHelpStatus.CONNECTED -> uiText("已开启", "Enabled")
        AccessibilityHelpStatus.NOT_CONNECTED -> uiText("未连接", "Not connected")
        AccessibilityHelpStatus.RESTRICTED -> uiText("受限制", "Restricted")
        AccessibilityHelpStatus.OFF -> uiText("未开启", "Not enabled")
    }
    val description = when (state.status) {
        AccessibilityHelpStatus.CONNECTED -> uiText("无障碍服务已连接。", "The accessibility service is connected.")
        AccessibilityHelpStatus.NOT_CONNECTED -> uiText("开关已开启，服务尚未连接。请在设置中关闭连页，再重新开启。", "The switch is on but the service is not connected. Turn Lianye off in Settings, then on again.")
        AccessibilityHelpStatus.RESTRICTED -> uiText("系统限制了此权限。先在应用信息中解除，再回来开启。", "The system has restricted this permission. Allow it in app information, then return to enable the service.")
        AccessibilityHelpStatus.OFF -> uiText("用于自动滚动、截取画面和显示悬浮球。", "Used to scroll, capture screen images and show controls.")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("无障碍", "Accessibility"), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium)
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = if (connected) MaterialTheme.colorScheme.secondary.copy(alpha = .10f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = .05f)) {
                        Text(status, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val device = stringResource(DeviceVendorDetector.getNameRes(vendor))
                Text(if (androidRelease.isBlank()) device else "$device · Android $androidRelease",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (blocked) RestrictedAccessSteps()
            if (connected) {
                OutlinedButton(onClick = onOpenSettings, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text(uiText("管理无障碍", "Manage accessibility"))
                }
            } else {
                LianyePrimaryButton(onClick = if (blocked) onOpenAppInfo else onOpenSettings,
                    enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(if (blocked) stringResource(R.string.btn_open_app_details) else uiText("前往设置", "Open settings"))
                }
            }
            if (!connected && !disconnected) {
                ExpandableSection(uiText("找不到连页？", "Cannot find Lianye?")) {
                    DeviceAccessibilitySteps(vendor)
                }
            }
            if (state.showRestrictedHelp && !blocked) {
                ExpandableSection(uiText("开关无法开启？", "Cannot turn on the switch?")) {
                    RestrictedAccessSteps()
                    TextButton(onClick = onOpenAppInfo, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.btn_open_app_details))
                    }
                }
            }
            if (blocked) TextButton(onClick = onOpenSettings, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(uiText("返回无障碍设置", "Return to accessibility"))
            }
            if (!connected && onUseManualCapture != null) TextButton(onClick = onUseManualCapture, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(uiText("使用自己滑动", "Use manual capture"))
            }
        }
    }
}

@Composable
internal fun DeviceAccessibilitySteps(vendor: DeviceVendor) {
    val accessibility = if (vendor == DeviceVendor.SAMSUNG) uiText("辅助功能", "Accessibility")
        else uiText("无障碍", "Accessibility")
    HelpStep(1, uiText("进入$accessibility", "Open $accessibility"))
    val list = stringResource(DeviceVendorDetector.getServiceListRes(vendor))
    HelpStep(2, uiText("打开「$list」", "Open $list"))
    val appName = stringResource(R.string.app_name)
    HelpStep(3, uiText("选择「$appName」，开启服务", "Select $appName and enable the service"))
    Text(stringResource(DeviceVendorDetector.getServiceListAliasRes(vendor)),
        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RestrictedAccessSteps() {
    HelpStep(1, uiText("打开连页的应用信息", "Open app information for Lianye"))
    HelpStep(2, uiText("更多 → 允许受限制的设置", "More → Allow restricted settings"))
    HelpStep(3, uiText("按系统提示验证，再返回开启", "Follow the verification prompt, then return to enable"))
    Text(uiText("没有此项时，以系统提示为准；也可使用自己滑动。", "If this option is missing, follow the system prompt or use manual capture."),
        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HelpStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top) {
        Text(number.toString().padStart(2, '0'), Modifier.widthIn(min = 26.dp),
            maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
