package org.lianye.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The same viewfinder and extending image relationship as the brand mark. */
@Composable
fun HomeEmptyHero(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val clickable = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    LongCaptureIllustration(clickable.fillMaxWidth().height(344.dp))
}
