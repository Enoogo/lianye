package org.lianye.ui.main.components

import android.content.Context
import android.os.Build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.domain.model.CaptureMode
import org.lianye.platform.AccessibilityHelpState
import org.lianye.platform.RestrictionStatus
import org.lianye.platform.DeviceVendor
import org.lianye.platform.DeviceVendorDetector
import org.lianye.ui.common.uiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(
    onDismissRequest: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    isServiceConnected: Boolean = false,
    isOverlayVisible: Boolean = false,
    onHideOverlay: () -> Unit = {},
    onStopService: () -> Unit = {},
    captureMode: CaptureMode = CaptureMode.AUTO,
    isBusy: Boolean = false,
    isModeBusy: Boolean = isBusy,
    onOpenAccessibilitySettings: () -> Unit = {},
    accessibilityHelpState: AccessibilityHelpState = AccessibilityHelpState.resolve(
        isServiceConnected, false, RestrictionStatus.UNKNOWN, Build.VERSION.SDK_INT),
    onUseManualCapture: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background, modifier = modifier) {
        AboutSheetContent(
            onOpenAppDetailsSettings, onOpenBatteryOptimization, onCopyAdbCommand,
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            isServiceConnected, isOverlayVisible, onHideOverlay, onStopService,
            captureMode, isBusy, isModeBusy, onOpenAccessibilitySettings, accessibilityHelpState, onUseManualCapture
        )
    }
}

@Composable
fun AboutSheetContent(
    onOpenAppDetailsSettings: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    modifier: Modifier = Modifier,
    isServiceConnected: Boolean = false,
    isOverlayVisible: Boolean = false,
    onHideOverlay: () -> Unit = {},
    onStopService: () -> Unit = {},
    captureMode: CaptureMode = CaptureMode.AUTO,
    isBusy: Boolean = false,
    isModeBusy: Boolean = isBusy,
    onOpenAccessibilitySettings: () -> Unit = {},
    accessibilityHelpState: AccessibilityHelpState = AccessibilityHelpState.resolve(
        isServiceConnected, false, RestrictionStatus.UNKNOWN, Build.VERSION.SDK_INT),
    onUseManualCapture: (() -> Unit)? = null,
    vendor: DeviceVendor = DeviceVendorDetector.currentVendor,
    androidRelease: String = Build.VERSION.RELEASE
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (captureMode == CaptureMode.MANUAL) uiText("手动长截图", "Manual capture")
                else uiText("自动长截图", "Automatic capture"), style = MaterialTheme.typography.titleSmall)
            Text(uiText("打开目标页面，点悬浮球上的「开始截图」。", "Open the page and tap Capture on the floating control."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (captureMode == CaptureMode.MANUAL)
                uiText("按指引向上滑，松手稍停。截够后点「结束」。", "Swipe up along the guide, release and pause. Tap Finish when you have enough.")
                else uiText("连页会自动滚动。截够后点「结束」。", "Lianye scrolls automatically. Tap Finish when you have enough."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (captureMode == CaptureMode.AUTO) {
            DeviceAccessibilityHelp(accessibilityHelpState, onOpenAccessibilitySettings,
                onOpenAppDetailsSettings, !isBusy,
                vendor = vendor, androidRelease = androidRelease,
                onUseManualCapture = onUseManualCapture.takeIf { !isModeBusy })
        } else {
            Text(uiText("自己滑动无需开启无障碍。", "Manual capture does not need accessibility."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isServiceConnected || isModeBusy) ExpandableSection(uiText("截图控制", "Capture controls")) {
            if (captureMode == CaptureMode.MANUAL) {
                OutlinedButton(onClick = onStopService, enabled = !isBusy, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText("停用本次截图", "Stop this capture session"))
                }
            } else {
                if (isOverlayVisible) SheetAction(R.string.home_hide_overlay, onHideOverlay, !isBusy)
                SheetAction(R.string.home_disable_service, onStopService, !isBusy)
                Text(uiText("隐藏按钮后，截图服务仍开启。", "Hiding the button keeps the service enabled."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HapticToggle()
        }
        DiagnosticsSection()
        AppAboutSection()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HapticToggle() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("overlay", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("haptics_enabled", true)) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(uiText("长按隐藏时振动", "Vibrate when long-press hides the button"),
            style = MaterialTheme.typography.bodyMedium)
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            prefs.edit().putBoolean("haptics_enabled", it).apply()
        })
    }
}

@Composable
internal fun ExpandableSection(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandedLabel = uiText("已展开", "Expanded")
    val collapsedLabel = uiText("已收起", "Collapsed")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { stateDescription = if (expanded) expandedLabel else collapsedLabel }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) content()
    }
}

@Composable
private fun SheetAction(label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) {
        Text(stringResource(label))
    }
}
