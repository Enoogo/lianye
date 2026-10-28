package org.lianye.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun LianyePrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(onClick, modifier.heightIn(min = 52.dp), enabled,
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        content = content)
}

@Composable
fun LianyeToolAction(label: String, @DrawableRes icon: Int, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick, Modifier.width(64.dp).heightIn(min = 52.dp), enabled,
        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
