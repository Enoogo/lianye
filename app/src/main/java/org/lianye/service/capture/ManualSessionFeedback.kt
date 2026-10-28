package org.lianye.service.capture

import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualControl
import org.lianye.domain.model.ManualGuide
import org.lianye.domain.model.ManualPhase

/** Pure presentation state: accepted content, rather than samples or touch events, teaches the guide. */
internal class ManualSessionFeedback(private val learned: Boolean) {
    @Volatile var state = ManualCaptureState()
        private set
    var guideDismissed = false
        private set
    var finishRequested = false
        private set
    var learnedThisSession = learned
        private set

    fun ready(control: ManualControl) {
        state = ManualCaptureState(phase = ManualPhase.READY, control = control)
    }

    fun control(control: ManualControl) { state = state.copy(control = control) }

    fun begin() {
        finishRequested = false
        state = state.copy(phase = ManualPhase.TAKING_FIRST, acceptedFrames = 0, guide = ManualGuide.HIDDEN, message = null)
    }

    fun accepted(count: Int) {
        if (count <= state.acceptedFrames) return
        if (count > 1 && state.control == ManualControl.OVERLAY) learnedThisSession = true
        val phase = when {
            finishRequested -> ManualPhase.FINISHING
            state.phase == ManualPhase.GAP -> ManualPhase.GAP
            else -> ManualPhase.RECORDING
        }
        val guide = when {
            state.control != ManualControl.OVERLAY || phase != ManualPhase.RECORDING || guideDismissed -> ManualGuide.HIDDEN
            state.message == "temporarily_unmatched" -> ManualGuide.RECOVERY
            learnedThisSession -> ManualGuide.ROUTE
            else -> ManualGuide.FULL
        }
        state = state.copy(phase = phase, acceptedFrames = count, guide = guide)
    }

    fun dismissGuide() {
        guideDismissed = true
        state = state.copy(guide = ManualGuide.HIDDEN)
    }

    fun gap() {
        state = state.copy(phase = if (finishRequested) ManualPhase.FINISHING else ManualPhase.GAP,
            guide = ManualGuide.HIDDEN, message = "alignment_failed")
    }

    fun temporaryUnmatched(unmatched: Boolean) {
        val guide = when {
            state.control != ManualControl.OVERLAY || finishRequested || guideDismissed -> ManualGuide.HIDDEN
            unmatched -> ManualGuide.RECOVERY
            learnedThisSession -> ManualGuide.ROUTE
            else -> ManualGuide.FULL
        }
        state = state.copy(phase = if (finishRequested) ManualPhase.FINISHING else ManualPhase.RECORDING,
            guide = guide, message = if (unmatched) "temporarily_unmatched" else null)
    }

    fun failedFirst() {
        state = state.copy(phase = ManualPhase.FIRST_FAILED, guide = ManualGuide.HIDDEN, message = "first_frame_failed")
    }

    fun reversed() {
        if (finishRequested || state.phase != ManualPhase.RECORDING) return
        state = state.copy(guide = if (state.control == ManualControl.OVERLAY && !guideDismissed)
            ManualGuide.FULL else ManualGuide.HIDDEN, message = null)
    }

    /** Returns true only when a first-frame callback already ran, so engine initialization cannot erase Stop. */
    fun finish(): Boolean {
        finishRequested = true
        state = state.copy(phase = ManualPhase.FINISHING, guide = ManualGuide.HIDDEN)
        return state.acceptedFrames > 0
    }
}
