package org.lianye.ui.main.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.ui.common.uiText

@Composable
internal fun AppAboutSection() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val noBrowser = uiText("未找到可用的浏览器", "No browser available")

    ExpandableSection(uiText("关于", "About")) {
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BrandMark(Modifier.size(42.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground)
                    if (version.isNotBlank()) Text(
                        version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                uiText("开源，无广告。截图仅在本机处理。", "Open source. No ads. Images stay on your device."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gxwane/lianye/releases"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(uiText("查看更新", "View updates"), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Icon(painterResource(R.drawable.ic_open_external), null, Modifier.size(18.dp))
                }
            }
        }
    }
}
