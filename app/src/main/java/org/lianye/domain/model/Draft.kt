package org.lianye.domain.model

import org.lianye.engine.model.TileMetadata

/** Coordinates are always in original image pixels; right and bottom are exclusive. */
data class ImageRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Int, y: Int) = x >= left && x < right && y >= top && y < bottom
    fun intersect(other: ImageRect): ImageRect? {
        val result = ImageRect(maxOf(left, other.left), maxOf(top, other.top), minOf(right, other.right), minOf(bottom, other.bottom))
        return result.takeIf { it.width > 0 && it.height > 0 }
    }
}

data class EditSnapshot(val crop: ImageRect? = null, val masks: List<ImageRect> = emptyList())

enum class CaptureCompletion {
    COMPLETED, USER_STOPPED, INTERRUPTED, STORAGE_FULL, SECURE_BLOCKED, PERMISSION_LOST, ALIGNMENT_FAILED
}

data class CaptureProgress(val frameCount: Int, val stitchedHeightPx: Int, val viewportHeightPx: Int)

data class Draft(
    val id: String,
    val tiles: List<TileMetadata>,
    val history: List<EditSnapshot> = listOf(EditSnapshot()),
    val editIndex: Int = 0,
    val revision: Int = 0,
    val savedRevision: Int = -1,
    val viewOffsetY: Float = 0f,
    val viewScale: Float = 1f,
    val completion: CaptureCompletion = CaptureCompletion.COMPLETED,
    /** The content actually exported, independent of monotonically increasing edit revisions. */
    val savedSnapshot: EditSnapshot? = null
) {
    val width get() = tiles.firstOrNull()?.width ?: 0
    val height get() = tiles.sumOf { it.height }
    val bounds get() = ImageRect(0, 0, width, height)
    val edits get() = history[editIndex]
    val crop get() = edits.crop?.intersect(bounds) ?: bounds
    val isSaved get() = savedSnapshot?.let { canonicalSnapshot() == canonicalSnapshot(it) }
        ?: (savedRevision == revision)
    val canUndo get() = editIndex > 0
    val canRedo get() = editIndex < history.lastIndex
    /** Full-image crops and mask ordering have the same output and share one representation. */
    fun canonicalSnapshot(snapshot: EditSnapshot = edits): EditSnapshot {
        val effectiveCrop = snapshot.crop?.intersect(bounds) ?: bounds
        val effectiveMasks = snapshot.masks.mapNotNull { it.intersect(effectiveCrop) }.distinct()
            .sortedWith(compareBy<ImageRect>({ it.top }, { it.left }, { it.bottom }, { it.right }))
        return EditSnapshot(effectiveCrop, effectiveMasks)
    }

    private fun savedSnapshotBeforeEdit(): EditSnapshot? = savedSnapshot
        ?: canonicalSnapshot().takeIf { savedRevision == revision }

    fun edit(snapshot: EditSnapshot) = copy(history = history.take(editIndex + 1) + snapshot,
        editIndex = editIndex + 1, revision = revision + 1, savedSnapshot = savedSnapshotBeforeEdit())
    fun undo() = if (canUndo) copy(editIndex = editIndex - 1, revision = revision + 1,
        savedSnapshot = savedSnapshotBeforeEdit()) else this
    fun redo() = if (canRedo) copy(editIndex = editIndex + 1, revision = revision + 1,
        savedSnapshot = savedSnapshotBeforeEdit()) else this
}
