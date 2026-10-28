package org.lianye.ui.floating

import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Wait for window drawing, rather than assuming an alpha property has reached the compositor. */
internal object CaptureWindowFrameFence {
    suspend fun awaitHiddenFrames(view: View): Long? = withContext(Dispatchers.Main.immediate) {
        if (!view.isAttachedToWindow) return@withContext 0L
        val firstCommit = submittedFrame(view) ?: return@withContext null
        // A second transparent submission also refreshes an otherwise static projection.
        // Use the first commit as the timestamp fence, keeping the second clean buffer.
        submittedFrame(view) ?: return@withContext null
        withTimeoutOrNull(350L) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val choreographer = Choreographer.getInstance()
                val callback = Choreographer.FrameCallback {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                choreographer.postFrameCallback(callback)
                continuation.invokeOnCancellation { view.post { choreographer.removeFrameCallback(callback) } }
            }
        } ?: return@withContext null
        firstCommit
    }

    private suspend fun submittedFrame(view: View): Long? = withTimeoutOrNull(350L) {
        suspendCancellableCoroutine { continuation ->
            val observer = view.viewTreeObserver
            if (view.isHardwareAccelerated) {
                val callback = Runnable {
                    if (continuation.isActive) continuation.resume(System.nanoTime())
                }
                observer.registerFrameCommitCallback(callback)
                continuation.invokeOnCancellation {
                    view.post { if (observer.isAlive) observer.unregisterFrameCommitCallback(callback) }
                }
            } else {
                var posted = false
                lateinit var listener: ViewTreeObserver.OnDrawListener
                listener = ViewTreeObserver.OnDrawListener {
                    if (!posted) {
                        posted = true
                        // Software drawing submits its Surface before this posted task runs.
                        view.post {
                            if (observer.isAlive) observer.removeOnDrawListener(listener)
                            if (continuation.isActive) continuation.resume(System.nanoTime())
                        }
                    }
                }
                observer.addOnDrawListener(listener)
                continuation.invokeOnCancellation {
                    view.post { if (observer.isAlive) observer.removeOnDrawListener(listener) }
                }
            }
            view.invalidate()
        }
    }
}
