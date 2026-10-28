package org.lianye.ui.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.lianye.ui.common.theme.LianyeAccent
import org.lianye.ui.common.uiText

/** One continuous screenshot extends beyond the single-screen viewfinder. */
@Composable
fun LongCaptureIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val accent = if (dark) colors.secondary else LianyeAccent
    val bodyInk = colors.onBackground.copy(alpha = if (dark) .24f else .17f)
    val paperOutline = colors.onBackground.copy(alpha = if (dark) .22f else .13f)
    val imageFill = accent.copy(alpha = if (dark) .14f else .10f)
    val description = uiText("一张长图向下延伸，越过单屏取景框", "A continuous image extends beyond one screen")
    Canvas(modifier.semantics { contentDescription = description }) {
        val scale = minOf(size.width / 300f, size.height / 328f)
        withTransform({
            translate((size.width - 300f * scale) / 2, (size.height - 328f * scale) / 2)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            val page = Offset(86f, 52f)
            val pageSize = Size(128f, 260f)
            val radius = CornerRadius(8f)
            val paper = Path().apply { addRoundRect(RoundRect(Rect(page, pageSize), 8f, 8f)) }
            drawRoundRect(Color.Black.copy(alpha = if (dark) .12f else .035f),
                page + Offset(0f, 4f), pageSize, radius)
            drawRoundRect(Color.Black.copy(alpha = if (dark) .08f else .025f),
                page + Offset(0f, 2f), pageSize, radius)
            drawRoundRect(colors.surface, page, pageSize, radius)
            drawRoundRect(paperOutline, page, pageSize, radius, style = Stroke(1f))

            fun row(x: Float, y: Float, width: Float, strong: Boolean = false) {
                drawRoundRect(if (strong) colors.onBackground.copy(alpha = .62f) else bodyInk,
                    Offset(x, y), Size(width, if (strong) 3.5f else 2.5f), CornerRadius(1.25f))
            }
            clipPath(paper) {
                drawRect(accent.copy(alpha = .025f), Offset(86f, 200f), Size(128f, 112f))
                row(100f, 70f, 52f, strong = true)
                row(100f, 83f, 28f)

                val thumbnail = Path().apply {
                    addRoundRect(RoundRect(Rect(Offset(100f, 103f), Size(100f, 52f)), 4f, 4f))
                }
                clipPath(thumbnail) {
                    drawRect(imageFill, Offset(100f, 103f), Size(100f, 52f))
                    val image = Path().apply {
                        moveTo(97f, 144f)
                        cubicTo(126f, 110f, 143f, 111f, 157f, 135f)
                        cubicTo(170f, 157f, 187f, 151f, 204f, 125f)
                        lineTo(204f, 158f); lineTo(97f, 158f); close()
                    }
                    drawPath(image, colors.onBackground.copy(alpha = if (dark) .16f else .12f))
                    val contour = Path().apply {
                        moveTo(97f, 127f)
                        cubicTo(124f, 98f, 143f, 99f, 159f, 121f)
                        cubicTo(175f, 143f, 192f, 135f, 204f, 115f)
                    }
                    drawPath(contour, colors.onBackground.copy(alpha = .25f), style = Stroke(1.1f))
                }
                row(100f, 170f, 100f); row(100f, 181f, 90f); row(100f, 192f, 61f)

                drawRoundRect(imageFill, Offset(100f, 220f), Size(24f, 26f), CornerRadius(3f))
                row(134f, 221f, 62f, strong = true); row(134f, 233f, 44f)
                row(100f, 265f, 58f, strong = true)
                row(100f, 279f, 100f); row(100f, 290f, 81f)
            }

            // The page continues through the frame boundary, matching the brand's extending strip.
            val continuation = Path().apply {
                moveTo(86f, 200f); lineTo(86f, 304f); quadraticTo(86f, 312f, 94f, 312f)
                lineTo(206f, 312f); quadraticTo(214f, 312f, 214f, 304f); lineTo(214f, 200f)
            }
            drawPath(continuation, accent, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (edge in listOf(86f, 214f)) {
                drawRoundRect(accent, Offset(edge - 1.5f, 195f), Size(3f, 14f), CornerRadius(1.5f))
            }
            val frame = Path().apply {
                moveTo(58f, 36f); lineTo(47f, 36f); quadraticTo(39f, 36f, 39f, 44f); lineTo(39f, 55f)
                moveTo(242f, 36f); lineTo(253f, 36f); quadraticTo(261f, 36f, 261f, 44f); lineTo(261f, 55f)
                moveTo(39f, 181f); lineTo(39f, 192f); quadraticTo(39f, 200f, 47f, 200f); lineTo(58f, 200f)
                moveTo(261f, 181f); lineTo(261f, 192f); quadraticTo(261f, 200f, 253f, 200f); lineTo(242f, 200f)
            }
            drawPath(frame, colors.onBackground.copy(alpha = .8f),
                style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
