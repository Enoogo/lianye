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

/** Compatibility component for existing callers. Primary actions advance capture setup. */
@Composable
fun MasterHeroControl(
    isConnected: Boolean,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(if (isConnected) R.string.status_service_active else R.string.status_service_inactive),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(if (isConnected) R.string.status_service_desc_active else R.string.status_service_desc_inactive),
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(if (isConnected) R.string.home_go_capture else R.string.home_enable))
            }
        }
    }
}
