package org.lianye.service.gesture

import android.graphics.Rect
import org.lianye.service.gesture.model.ControlledScrollCommand

/**
 * Builds the injected scroll stroke for automatic capture.
 *
 * The floating control is an overlay window: a gesture that starts on top of it is consumed
 * by the button instead of scrolling the page. That looked exactly like "the page reached the
 * bottom after one or two frames". The stroke therefore starts at a horizontal position that
 * does not overlap the control, while staying inside the selected band.
 *
 * Pure Kotlin (Rect field reads + local maths only) so plain JVM unit tests can run without
 * Android framework method bodies (Rect.width()/height()/intersects() are stubs there).
 */
object AutoScrollCommandFactory {

    private val CANDIDATE_X = floatArrayOf(0.5f, 0.32f, 0.68f, 0.18f, 0.82f)

    fun build(region: Rect, avoid: Rect?, density: Float): ControlledScrollCommand {
        val width = (region.right - region.left).toFloat().coerceAtLeast(1f)
        val bandHeight = (region.bottom - region.top).toFloat().coerceAtLeast(1f)
        val stroke = minOf(bandHeight * 0.35f, 280f * density)
        val centerY = region.top + bandHeight / 2f
        val margin = 24f * density

        val top = minOf(centerY - stroke / 2f, centerY + stroke / 2f)
        val bottom = maxOf(centerY - stroke / 2f, centerY + stroke / 2f)

        fun overlaps(px: Float): Boolean {
            if (avoid == null) return false
            val ax0 = avoid.left - margin
            val ay0 = avoid.top - margin
            val ax1 = avoid.right + margin
            val ay1 = avoid.bottom + margin
            // AABB intersection: stroke column (px - margin .. px + margin) x (top .. bottom).
            return px - margin < ax1 && px + margin > ax0 && top < ay1 && bottom > ay0
        }

        val x = CANDIDATE_X
            .map { region.left + width * it }
            .firstOrNull { px -> !overlaps(px) }
            ?: (region.left + width * 0.5f)

        return ControlledScrollCommand(
            startX = x,
            startY = centerY + stroke / 2f,
            endX = x,
            endY = centerY - stroke / 2f,
            moveDurationMs = 550L,
            holdDurationMs = 150L
        )
    }
}
