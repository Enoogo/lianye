package org.lianye.ui.main.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.lianye.platform.LianyeLogStore
import org.lianye.ui.common.uiText

/**
 * Diagnostics section: lets the user read the full engine/gesture capture log from inside
 * the app and copy it to the clipboard so it can be pasted into a bug report.
 */
@Composable
internal fun DiagnosticsSection() {
    val context = LocalContext.current
    var logText by remember { mutableStateOf(LianyeLogStore.dump()) }
    val copied = uiText("日志已复制到剪贴板", "Log copied to clipboard")
    val empty = uiText("暂无日志。先做一次截图，再回到这里。", "No log entries yet. Run a capture, then come back.")

    ExpandableSection(uiText("诊断日志", "Diagnostics log")) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { logText = LianyeLogStore.dump() },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(uiText("刷新", "Refresh")) }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("lianye-diagnostics", logText))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(uiText("复制全部", "Copy all")) }

                OutlinedButton(
                    onClick = {
                        LianyeLogStore.clear()
                        logText = LianyeLogStore.dump()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(uiText("清空", "Clear")) }
            }

            Text(
                if (logText == "(no log entries yet)") empty else logText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            )

            Text(
                uiText(
                    "包含每帧匹配位移、停止判定原因、手势注入结果。截图时页面不动或提前停止时，把这里的内容复制给我。",
                    "Includes per-frame match delta, stop decision reasons, and gesture injection results. Copy this when a capture stalls or stops early."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
