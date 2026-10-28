package org.lianye.ui.main.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.lianye.R
import org.lianye.ui.common.theme.LianyeGreen
import org.lianye.ui.common.theme.LianyeGold
import org.lianye.ui.common.theme.LianyeTheme

@Composable
fun MediaProjectionStatusCard(
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isGranted) LianyeGreen else LianyeGold,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.media_projection_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.media_projection_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.btn_grant_media_projection))
                }
            }
        }
    }
}

@Preview(name = "Projection Granted (Light)", group = "MediaProjectionCard", showBackground = true)
@Composable
private fun PreviewMediaProjectionStatusCardGranted() {
    LianyeTheme(dynamicColor = false) {
        MediaProjectionStatusCard(
            isGranted = true,
            onRequestPermission = {}
        )
    }
}

@Preview(name = "Projection Not Granted (Light)", group = "MediaProjectionCard", showBackground = true)
@Preview(name = "Projection Not Granted (Dark)", group = "MediaProjectionCard", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewMediaProjectionStatusCardNotGranted() {
    LianyeTheme(dynamicColor = false) {
        MediaProjectionStatusCard(
            isGranted = false,
            onRequestPermission = {}
        )
    }
}
