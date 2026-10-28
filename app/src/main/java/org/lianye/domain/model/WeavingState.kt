package org.lianye.domain.model

import org.lianye.engine.model.TileMetadata

sealed interface WeavingState {
    data class Idle(val isServiceConnected: Boolean = false) : WeavingState
    data class Weaving(val frameCount: Int = 0, val currentHeightPx: Int = 0, val viewportHeightPx: Int = 0) : WeavingState
    data class Preview(val tiles: List<TileMetadata> = emptyList(), val totalHeightPx: Int = 0) : WeavingState
    data class Error(val message: String, val partialTiles: List<TileMetadata> = emptyList()) : WeavingState
}

sealed interface LianyeAction {
    data object Start : LianyeAction
    data object Stop : LianyeAction
    data object Reset : LianyeAction
    data class ReportError(val message: String) : LianyeAction
}
