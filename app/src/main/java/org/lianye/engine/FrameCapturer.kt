package org.lianye.engine

import org.lianye.engine.model.PixelSlice
import org.lianye.domain.model.CaptureCompletion

/**
 * Pure Kotlin contract for frame acquisition, decoupled from Android Framework specifics.
 */
enum class CaptureSpan { BAND, FIRST, TOP_ONLY, BOTTOM_ONLY }

interface FrameCapturer {
    val failureReason: CaptureCompletion get() = CaptureCompletion.PERMISSION_LOST
    /** Y of the first captured row in screen coordinates; non-zero when a region is selected. */
    val contentTopInScreen: Int get() = 0

    /** True when the user enabled full-height stitching. */
    val fullHeightMode: Boolean get() = false

    /** Chooses which vertical slice the next frame returns (band or an extension strip). */
    fun setCaptureSpan(span: CaptureSpan) {}
    suspend fun captureFrame(): PixelSlice?
    /** Raw samples may contain controls. They are used for motion/stability only. */
    suspend fun sampleFrame(): FrameSample = captureFrame()?.let(FrameSample::Available)
        ?: FrameSample.Failed(failureReason)

    /** A candidate is committed only after acquisition without our own controls. */
    suspend fun captureCleanFrame(candidate: PixelSlice): FrameSample = FrameSample.Available(candidate)
    fun prepareSession() {}
    fun releaseSession() {}
    fun release() {}
}

sealed interface FrameSample {
    data class Available(val frame: PixelSlice) : FrameSample
    /** An unchanged display or a temporary empty ImageReader is not a lost permission. */
    data object NoNewFrame : FrameSample
    data class Failed(val reason: CaptureCompletion) : FrameSample
}
