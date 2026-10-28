package org.lianye.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.lianye.ui.common.LianyePrimaryButton
import org.lianye.ui.common.uiText

@Composable
fun ReplaceDraftDialog(onDismiss: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    // A Dialog is a separate Android window; preserve the host's per-app language and font scaling.
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides context, LocalDensity provides density) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.background,
                modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(uiText("当前长图未保存", "Current image is not saved"), style = MaterialTheme.typography.titleLarge)
                    Text(uiText("开始新的截图会替换当前长图。", "A new capture will replace this image."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LianyePrimaryButton(onSave, Modifier.fillMaxWidth()) { Text(uiText("保存并新建", "Save and start")) }
                        OutlinedButton(onDiscard, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(uiText("放弃并新建", "Discard and start"))
                        }
                        TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("取消", "Cancel")) }
                    }
                }
            }
        }
    }
}
