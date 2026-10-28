package org.lianye.ui.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.ui.common.uiText
import org.lianye.ui.common.theme.LianyeAccent

@Composable
fun MainTopBar(onShowAbout: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(Modifier.size(26.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            TextButton(onClick = onShowAbout) { Text(uiText("帮助", "Help"), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Same geometry as ic_main_header_shuttle, with theme ink instead of a light backing plate. */
@Composable
internal fun BrandMark(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = if (colors.background.luminance() < .5f) colors.secondary else LianyeAccent
    val corners = remember {
        Path().apply {
            moveTo(29f, 12f); lineTo(20f, 12f); cubicTo(18.343f, 12f, 17f, 13.343f, 17f, 15f); lineTo(17f, 24f)
            moveTo(71f, 12f); lineTo(80f, 12f); cubicTo(81.657f, 12f, 83f, 13.343f, 83f, 15f); lineTo(83f, 24f)
            moveTo(17f, 50f); lineTo(17f, 59f); cubicTo(17f, 60.657f, 18.343f, 62f, 20f, 62f); lineTo(25f, 62f)
            moveTo(83f, 50f); lineTo(83f, 59f); cubicTo(83f, 60.657f, 81.657f, 62f, 80f, 62f); lineTo(75f, 62f)
        }
    }
    Canvas(modifier) {
        withTransform({ scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero); translate(top = -3f) }) {
            val stroke = Stroke(5.8f, join = StrokeJoin.Round)
            drawPath(corners, colors.onBackground, style = stroke)
            drawRoundRect(accent, Offset(36f, 22f), Size(28f, 72f), CornerRadius(3f), style = stroke)
        }
    }
}

@Composable
fun MainHeader(modifier: Modifier = Modifier, onShowAbout: () -> Unit = {}) {
    MainTopBar(onShowAbout, modifier)
}
