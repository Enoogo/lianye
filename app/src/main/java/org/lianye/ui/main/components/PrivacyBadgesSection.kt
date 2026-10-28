package org.lianye.ui.main.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lianye.R

@Composable
fun PrivacyBadgesSection(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BadgeItem(R.drawable.ic_privacy_zero_net, stringResource(R.string.privacy_badge_zero_net),
            stringResource(R.string.privacy_badge_zero_net_sub))
        BadgeItem(R.drawable.ic_privacy_blind, stringResource(R.string.privacy_badge_blind),
            stringResource(R.string.privacy_badge_blind_sub))
        BadgeItem(R.drawable.ic_privacy_offline, stringResource(R.string.privacy_badge_local),
            stringResource(R.string.privacy_badge_local_sub))
    }
}

@Composable
internal fun BadgeItem(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
