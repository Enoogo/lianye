package org.lianye.ui.preview

import org.lianye.domain.model.ImageRect
import kotlin.math.ceil
import kotlin.math.floor

/** Shared source-pixel math for canvas, editing and persisted viewport state. */
internal object PreviewGeometry {
    fun selection(x1: Float, y1: Float, x2: Float, y2: Float, bounds: ImageRect): ImageRect? {
        val rect = ImageRect(
            floor(minOf(x1, x2)).toInt(), floor(minOf(y1, y2)).toInt(),
            ceil(maxOf(x1, x2)).toInt(), ceil(maxOf(y1, y2)).toInt()
        )
        return rect.intersect(bounds)
    }

    fun maxOffset(bounds: ImageRect, viewportHeight: Float, pixelScale: Float): Float =
        maxOf(bounds.top.toFloat(), bounds.bottom - viewportHeight / pixelScale.coerceAtLeast(0.0001f))

    fun clampOffset(offset: Float, bounds: ImageRect, viewportHeight: Float, pixelScale: Float) =
        offset.coerceIn(bounds.top.toFloat(), maxOffset(bounds, viewportHeight, pixelScale))
}
