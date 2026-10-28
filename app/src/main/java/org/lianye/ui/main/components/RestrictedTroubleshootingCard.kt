package org.lianye.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.ui.main.RestrictedCardPhase

/** A missing or unknown restriction result never claims successful authorization. */
@Composable
fun RestrictedTroubleshootingCard(
    phase: RestrictedCardPhase,
    onOpenAppDetailsSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (phase == RestrictedCardPhase.HIDDEN) return
    val returnToSettings = phase == RestrictedCardPhase.ALLOWED_READY_RETURN
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (returnToSettings) R.string.troubleshoot_title_allowed else R.string.troubleshoot_title_blocked),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                stringResource(if (returnToSettings) R.string.troubleshoot_desc_allowed else R.string.troubleshoot_desc_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (!returnToSettings) {
                listOf(R.string.troubleshoot_step_1, R.string.troubleshoot_step_2, R.string.troubleshoot_step_3).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = if (returnToSettings) onOpenAccessibilitySettings else onOpenAppDetailsSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(if (returnToSettings) R.string.troubleshoot_btn_return_a11y else R.string.troubleshoot_btn_grant))
            }
        }
    }
}
