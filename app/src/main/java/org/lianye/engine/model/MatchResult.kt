package org.lianye.engine.model

data class MatchResult(
    val deltaY: Int,
    val sadScore: Float,
    val ambiguityRatio: Float,
    val isBottomReached: Boolean,
    val isSecureBlocked: Boolean = false,
    val isStationary: Boolean = false,
    val isDynamicScene: Boolean = false,
    /**
     * True when the alignment produced a low displacement but a residual far above
     * what a clean "bottom reached" match can produce. This is the signature of a
     * deformed overlap (capture-timing jitter, motion blur, or a region whose top
     * edge is not consistent between frames) and must NOT be treated as "the page
     * stopped moving" — the engine retries instead of terminating the capture.
     */
    val isDeformedMatch: Boolean = false
)
