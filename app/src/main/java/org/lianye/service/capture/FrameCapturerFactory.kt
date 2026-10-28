package org.lianye.service.capture

import android.accessibilityservice.AccessibilityService
import android.os.Build
import org.lianye.engine.FrameCapturer

/**
 * Factory providing the optimal FrameCapturer strategy depending on Android OS version.
 * - API 30+ (Android 11~15): AccessibilityFrameCapturer (100% silent, zero-dialog)
 * - API 29 (Android 10): MediaProjectionFrameCapturer (with Skia SIMD stride correction)
 */
object FrameCapturerFactory {
    fun create(
        service: AccessibilityService,
        onPreCapture: (suspend () -> Long?)? = null,
        onPostCapture: (suspend () -> Unit)? = null
    ): FrameCapturer {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityFrameCapturer(
                service = service,
                onPreCapture = onPreCapture,
                onPostCapture = onPostCapture
            )
        } else {
            MediaProjectionFrameCapturer(
                context = service,
                onPreCapture = onPreCapture,
                onPostCapture = onPostCapture
            )
        }
    }
}
