package org.lianye.ui.preview

import org.lianye.domain.model.ImageRect

internal enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** A crop gesture moves one corner; its opposite corner remains fixed. */
internal object CropGeometry {
    fun moveCorner(rect: ImageRect, corner: CropCorner, x: Float, y: Float, bounds: ImageRect): ImageRect {
        val left = if (corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT)
            x.toInt().coerceIn(bounds.left, rect.right - 1) else rect.left
        val right = if (corner == CropCorner.TOP_RIGHT || corner == CropCorner.BOTTOM_RIGHT)
            x.toInt().coerceIn(rect.left + 1, bounds.right) else rect.right
        val top = if (corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT)
            y.toInt().coerceIn(bounds.top, rect.bottom - 1) else rect.top
        val bottom = if (corner == CropCorner.BOTTOM_LEFT || corner == CropCorner.BOTTOM_RIGHT)
            y.toInt().coerceIn(rect.top + 1, bounds.bottom) else rect.bottom
        return ImageRect(left, top, right, bottom)
    }
}

/** Local history only. No draft is changed until the user applies this session. */
internal data class CropHistory(val entries: List<ImageRect>, val index: Int = 0) {
    val current get() = entries[index]
    val canUndo get() = index > 0
    val canRedo get() = index < entries.lastIndex
    fun change(rect: ImageRect): CropHistory {
        if (rect == current) return this
        val next = (entries.take(index + 1) + rect).takeLast(100)
        return CropHistory(next, next.lastIndex)
    }
    fun undo() = if (canUndo) copy(index = index - 1) else this
    fun redo() = if (canRedo) copy(index = index + 1) else this
}
