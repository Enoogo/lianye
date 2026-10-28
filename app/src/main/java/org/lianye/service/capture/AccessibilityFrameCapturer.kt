package org.lianye.service.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.lianye.engine.CaptureSpan
import org.lianye.engine.FrameCapturer
import org.lianye.engine.model.PixelSlice
import org.lianye.domain.model.CaptureCompletion
import org.lianye.platform.CaptureRegionStore
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@RequiresApi(Build.VERSION_CODES.R)
class AccessibilityFrameCapturer(
    private val service: AccessibilityService,
    var onPreCapture: (suspend () -> Long?)? = null,
    var onPostCapture: (suspend () -> Unit)? = null
) : FrameCapturer {

    override var failureReason = CaptureCompletion.PERMISSION_LOST
        private set

    private val executor = Executors.newSingleThreadExecutor()

    override val contentTopInScreen: Int
        get() = CaptureRegionStore.load(service).top

    override val fullHeightMode: Boolean
        get() = CaptureRegionStore.isFullHeightMode(service)

    @Volatile
    private var captureSpan = CaptureSpan.BAND

    override fun setCaptureSpan(span: CaptureSpan) { captureSpan = span }

    override suspend fun captureFrame(): PixelSlice? {
        try {
            if (onPreCapture?.let { it() == null } == true) {
                failureReason = CaptureCompletion.INTERRUPTED
                return null
            }
            return suspendCancellableCoroutine { continuation ->
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val slice = extractPixelSlice(result)
                            if (continuation.isActive) {
                                continuation.resume(slice)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            failureReason = if (Build.VERSION.SDK_INT >= 34 && errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                                CaptureCompletion.SECURE_BLOCKED
                            } else CaptureCompletion.PERMISSION_LOST
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                )
            }
        } finally {
            withContext(NonCancellable) { onPostCapture?.invoke() }
        }
    }

    private fun extractPixelSlice(result: AccessibilityService.ScreenshotResult): PixelSlice? {
        val buffer = result.hardwareBuffer
        val colorSpace = result.colorSpace
        var hwBitmap: Bitmap? = null
        var softBitmap: Bitmap? = null
        return try {
            hwBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
            val w = hwBitmap.width
            val fullHeight = hwBitmap.height
            // takeScreenshot always returns the whole display, navigation bar included.
            // Crop it here so its translucent strip is never woven into the long image,
            // then apply the user's capture rectangle (default: everything above the bar).
            val fullDisplay = SystemBarCrop.isFullDisplayFrame(service, w, fullHeight)
            val region = if (fullDisplay) CaptureRegionStore.load(service) else null
            val left = (region?.left ?: 0).coerceIn(0, w - 1)
            val right = (region?.right ?: w).coerceIn(left + 1, w)
            val bandTop = (region?.top ?: 0).coerceIn(0, fullHeight - 1)
            val bandBottom = (region?.bottom ?: fullHeight).coerceIn(bandTop + 1, fullHeight)
            // Full-height stitching extends each end once, so the band itself stays clean.
            val top = when (captureSpan) {
                CaptureSpan.TOP_ONLY, CaptureSpan.FIRST -> 0
                CaptureSpan.BAND -> bandTop
                CaptureSpan.BOTTOM_ONLY -> bandBottom
            }
            val bottom = when (captureSpan) {
                CaptureSpan.TOP_ONLY -> bandTop
                CaptureSpan.BOTTOM_ONLY -> fullHeight
                CaptureSpan.BAND, CaptureSpan.FIRST -> bandBottom
            }
            val h = bottom - top
            val pixels = IntArray(w * h)
            softBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            softBitmap.getPixels(pixels, 0, w, 0, top, w, h)
            PixelSlice(pixels, right - left, h, stride = w, offset = left)
        } catch (t: Throwable) {
            null
        } finally {
            softBitmap?.recycle()
            hwBitmap?.recycle()
            buffer.close()
        }
    }

    override fun releaseSession() {
        captureSpan = CaptureSpan.BAND
    }

    override fun release() {
        captureSpan = CaptureSpan.BAND
        executor.shutdown()
    }
}
