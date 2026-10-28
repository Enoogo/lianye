package org.lianye.service.gesture.model

data class ControlledScrollCommand(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val moveDurationMs: Long = 600L,
    val holdDurationMs: Long = 150L
)
