package org.lianye.service.gesture

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.lianye.service.gesture.model.ControlledScrollCommand
import kotlin.coroutines.resume

class AntiFlingController(
    private val dispatcher: GestureDispatcher,
    private val timeoutMs: Long = 2500L
) {
    suspend fun executeControlledScroll(command: ControlledScrollCommand): Boolean {
        Log.d(TAG, "executeControlledScroll: timeoutMs=$timeoutMs")
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                // P0 Guard: Ensure touch queue is released upon timeout or parent cancellation
                continuation.invokeOnCancellation {
                    Log.w(TAG, "executeControlledScroll cancelled, dispatching cancel gesture")
                    dispatcher.sendCancelGesture()
                }

                val dispatched = dispatcher.sendGesture(command) { success ->
                    Log.d(TAG, "executeControlledScroll callback received: success=$success")
                    if (continuation.isActive) {
                        continuation.resume(success)
                    }
                }

                if (!dispatched && continuation.isActive) {
                    Log.e(TAG, "executeControlledScroll: sendGesture returned false immediately!")
                    continuation.resume(false)
                }
            }
        }

        return if (result == null) {
            Log.e(TAG, "executeControlledScroll watchdog timed out after ${timeoutMs}ms!")
            // Watchdog timeout recovery
            dispatcher.sendCancelGesture()
            false
        } else {
            result
        }
    }

    fun cancelCurrentGesture(): Boolean = dispatcher.sendCancelGesture()

    companion object {
        private const val TAG = "AntiFling"
    }
}
