package org.lianye.service.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import org.lianye.service.gesture.model.ControlledScrollCommand

import android.util.Log

class AccessibilityGestureDispatcher(
    private val service: AccessibilityService
) : GestureDispatcher {

    override fun sendGesture(command: ControlledScrollCommand, callback: (Boolean) -> Unit): Boolean {
        Log.d(TAG, "sendGesture Phase 1 (Move): (${command.startX}, ${command.startY}) -> (${command.endX}, ${command.endY}), duration=${command.moveDurationMs}ms")
        val movePath = Path().apply {
            moveTo(command.startX, command.startY)
            lineTo(command.endX, command.endY)
        }
        val moveStroke = GestureDescription.StrokeDescription(
            movePath, 0L, command.moveDurationMs, true
        )
        val moveGesture = GestureDescription.Builder()
            .addStroke(moveStroke)
            .build()

        val dispatched = service.dispatchGesture(
            moveGesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Phase 1 (Move) completed. Dispatching Phase 2 (Hold): duration=${command.holdDurationMs}ms")
                    org.lianye.platform.LianyeLogStore.log("D", TAG, "Phase 1 (Move) completed")
                    // Phase 2: Hold at end position with +0.5f subpixel micro-displacement to avoid zero-length Path validation errors on OEM ROMs
                    val holdPath = Path().apply {
                        moveTo(command.endX, command.endY)
                        lineTo(command.endX, command.endY + 0.5f)
                    }
                    try {
                        val holdStroke = moveStroke.continueStroke(
                            holdPath, 0L, command.holdDurationMs, false
                        )
                        val holdGesture = GestureDescription.Builder()
                            .addStroke(holdStroke)
                            .build()

                        val holdDispatched = service.dispatchGesture(
                            holdGesture,
                            object : AccessibilityService.GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    Log.d(TAG, "Phase 2 (Hold) completed successfully.")
                                    org.lianye.platform.LianyeLogStore.log("D", TAG, "Phase 2 (Hold) completed")
                                    callback(true)
                                }

                                override fun onCancelled(gestureDescription: GestureDescription?) {
                                    Log.w(TAG, "Phase 2 (Hold) cancelled. Falling back to success since Phase 1 moved.")
                                    org.lianye.platform.LianyeLogStore.log("W", TAG, "Phase 2 (Hold) cancelled -> success fallback")
                                    callback(true)
                                }
                            },
                            null
                        )

                        if (!holdDispatched) {
                            Log.w(TAG, "Phase 2 (Hold) dispatch returned false. Falling back to success.")
                            callback(true)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Phase 2 (Hold) continueStroke failed with exception, falling back to success", e)
                        callback(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Phase 1 (Move) cancelled by system.")
                    callback(false)
                }
            },
            null
        )

        if (!dispatched) {
            Log.e(TAG, "Phase 1 (Move) service.dispatchGesture returned false immediately!")
        }
        return dispatched
    }

    override fun sendCancelGesture(): Boolean {
        // AOSP AccessibilityService does not support cancelling gestures via virtual taps.
        // We intentionally avoid sending a (0, 0) tap which could click the app's top-left back/close button.
        return true
    }

    companion object {
        private const val TAG = "LianyeGesture"
    }
}
