package org.lianye.domain.model

enum class CaptureMode { AUTO, MANUAL }
enum class ManualPhase { IDLE, PREPARING, READY, TAKING_FIRST, RECORDING, FIRST_FAILED, GAP, FINISHING }
enum class ManualGuide { FULL, ROUTE, RECOVERY, HIDDEN }
enum class ManualControl { OVERLAY, NOTIFICATION, UNAVAILABLE }

data class ManualCaptureState(
    val phase: ManualPhase = ManualPhase.IDLE,
    val acceptedFrames: Int = 0,
    val guide: ManualGuide = ManualGuide.HIDDEN,
    val control: ManualControl = ManualControl.UNAVAILABLE,
    val message: String? = null
)
