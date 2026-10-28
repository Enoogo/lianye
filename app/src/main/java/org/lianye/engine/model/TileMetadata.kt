package org.lianye.engine.model

import java.io.File

/**
 * Immutable metadata representing a persisted tile chunk on disk.
 */
data class TileMetadata(
    val index: Int,
    val file: File,
    val width: Int,
    val height: Int,
    val startY: Int
)
