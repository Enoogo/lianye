package org.lianye.service.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.lianye.domain.model.CaptureCompletion
import org.lianye.platform.CaptureRegionStore
import org.lianye.engine.CaptureSpan
import org.lianye.engine.FrameCapturer
import org.lianye.engine.FrameSample
import org.lianye.engine.model.PixelSlice

/**
 * API29+ screen capture backed by one MediaProjection VirtualDisplay per authorization.
 *
 * Implements Skia SIMD row stride alignment and color swizzle remediation for
 * devices enforcing non-standard row padding (e.g. Kirin 710F Mali-G51 64-byte alignment).
 */
class MediaProjectionFrameCapturer(
    private val context: Context,
    var onPreCapture: (suspend () -> Long?)? = null,
    var onPostCapture: (suspend () -> Unit)? = null
) : FrameCapturer {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureHandlerThread: HandlerThread? = null
    private var reusableBitmap: Bitmap? = null
    private val frameSignal = Channel<Unit>(Channel.CONFLATED)
    private val acquisitionLock = Mutex()
    @Volatile private var contentVisible = true
    @Volatile private var requestedSize: Pair<Int, Int>? = null
    @Volatile private var sessionInvalid = false
    override val failureReason: CaptureCompletion
        get() = if (MediaProjectionHolder.isReady() && !sessionInvalid) CaptureCompletion.INTERRUPTED else CaptureCompletion.PERMISSION_LOST

    override val contentTopInScreen: Int
        get() = CaptureRegionStore.load(context).top

    override val fullHeightMode: Boolean
        get() = CaptureRegionStore.isFullHeightMode(context)

    @Volatile
    private var captureSpan = CaptureSpan.BAND

    override fun setCaptureSpan(span: CaptureSpan) { captureSpan = span }

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    init {
        updateScreenMetrics()
    }

    private fun updateScreenMetrics() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.maximumWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        screenDensity = context.resources.configuration.densityDpi
    }

    override fun prepareSession() {
        if (virtualDisplay == null) updateScreenMetrics()
    }

    fun setContentVisible(visible: Boolean) { contentVisible = visible }

    /** Resize the existing display: an Android 14 projection token cannot create a second one. */
    fun resizeContent(width: Int, height: Int) {
        if (width > 0 && height > 0) requestedSize = width to height
    }

    private fun applyPendingResize() {
        val (width, height) = requestedSize ?: return
        requestedSize = null
        if (width == screenWidth && height == screenHeight) return
        screenWidth = width
        screenHeight = height
        val display = virtualDisplay ?: return
        val thread = captureHandlerThread ?: return
        val oldReader = imageReader
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ frameSignal.trySend(Unit) }, Handler(thread.looper))
        display.resize(width, height, screenDensity)
        display.surface = reader.surface
        imageReader = reader
        oldReader?.close()
        reusableBitmap?.recycle()
        reusableBitmap = null
        while (frameSignal.tryReceive().isSuccess) { /* Ignore buffers from the old surface. */ }
    }

    private fun createSession() {
        if (virtualDisplay != null) return

        val projection = MediaProjectionHolder.get()
        if (projection == null) {
            Log.e(TAG, "prepareSession failed: MediaProjection is not available")
            return
        }

        if (requestedSize == null) updateScreenMetrics()
        requestedSize?.let { (width, height) ->
            screenWidth = width
            screenHeight = height
            requestedSize = null
        }
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.e(TAG, "prepareSession failed: Invalid screen dimensions ($screenWidth x $screenHeight)")
            return
        }

        val thread = HandlerThread("Lianye_Capture").apply { start() }
        captureHandlerThread = thread
        val handler = Handler(thread.looper)

        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({
            frameSignal.trySend(Unit)
        }, handler)
        imageReader = reader

        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
        try {
            virtualDisplay = projection.createVirtualDisplay("Lianye_VD", screenWidth, screenHeight,
                screenDensity, flags, reader.surface, null, handler)
        } catch (error: RuntimeException) {
            // A rejected/consumed token cannot be made valid by retrying createVirtualDisplay.
            sessionInvalid = true
            reader.close()
            imageReader = null
            thread.quitSafely()
            captureHandlerThread = null
            throw error
        }
        Log.i(TAG, "VirtualDisplay session created: ${screenWidth}x$screenHeight @ ${screenDensity}dpi")
    }

    override suspend fun captureFrame(): PixelSlice? = when (val result = acquireCleanFrame()) {
        is FrameSample.Available -> result.frame
        else -> null
    }

    /** Raw samples can contain our controls; the manual engine must never persist these. */
    override suspend fun sampleFrame(): FrameSample = acquisitionLock.withLock {
        if (!MediaProjectionHolder.isReady() || sessionInvalid) return@withLock FrameSample.Failed(CaptureCompletion.PERMISSION_LOST)
        if (!contentVisible) return@withLock FrameSample.NoNewFrame
        try {
            if (imageReader == null) createSession()
            applyPendingResize()
            val reader = imageReader ?: return@withLock FrameSample.Failed(failureReason)
            var image = reader.acquireLatestImage()
            if (image == null) {
                withTimeoutOrNull(120L) { frameSignal.receive() }
                image = reader.acquireLatestImage()
            }
            image?.let { processImage(it) }?.let { FrameSample.Available(it) } ?: FrameSample.NoNewFrame
        } catch (error: RuntimeException) {
            Log.w(TAG, "Raw frame unavailable", error)
            FrameSample.Failed(failureReason)
        }
    }

    override suspend fun captureCleanFrame(candidate: PixelSlice): FrameSample = acquireCleanFrame()

    private suspend fun acquireCleanFrame(): FrameSample = acquisitionLock.withLock {
        if (!MediaProjectionHolder.isReady() || sessionInvalid) return@withLock FrameSample.Failed(CaptureCompletion.PERMISSION_LOST)
        if (!contentVisible) return@withLock FrameSample.NoNewFrame
        // Drop queued frames before hiding controls; the first display is created only
        // after the hidden-window transaction has settled, so its first buffer is clean.
        imageReader?.acquireLatestImage()?.close()
        while (frameSignal.tryReceive().isSuccess) {
            // A previous visible overlay must not satisfy the next frame wait.
        }
        try {
            val hiddenAfter = onPreCapture?.let { it() ?: return@withLock FrameSample.NoNewFrame } ?: 0L
            if (imageReader == null) createSession()
            applyPendingResize()
            val reader = imageReader ?: return@withLock FrameSample.Failed(failureReason)
            // The window fence submitted two transparent frames. Keep its final buffer:
            // a static page need not produce another frame after the windows are hidden.
            val image = withTimeoutOrNull(600L) {
                var fresh: Image? = null
                while (fresh == null) {
                    val next = reader.acquireLatestImage()
                    if (next != null) {
                        if (next.timestamp <= 0L || next.timestamp < hiddenAfter) next.close()
                        else fresh = next
                    }
                    if (fresh == null) frameSignal.receive()
                }
                fresh
            }

            if (image == null) {
                Log.w(TAG, "captureFrame timed out waiting for display buffer")
                return@withLock if (MediaProjectionHolder.isReady()) FrameSample.NoNewFrame
                    else FrameSample.Failed(CaptureCompletion.PERMISSION_LOST)
            }
            processImage(image)?.let { FrameSample.Available(it) } ?: FrameSample.Failed(failureReason)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            Log.w(TAG, "Clean frame unavailable", error)
            FrameSample.Failed(failureReason)
        } finally {
            withContext(NonCancellable) { onPostCapture?.invoke() }
        }
    }

    private fun processImage(image: Image): PixelSlice? {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = rowStride / pixelStride
            val imgWidth = image.width
            val imgHeight = image.height

            var bmp = reusableBitmap
            if (bmp == null || bmp.width != paddedWidth || bmp.height != imgHeight || bmp.isRecycled) {
                bmp?.recycle()
                bmp = Bitmap.createBitmap(paddedWidth, imgHeight, Bitmap.Config.ARGB_8888)
                reusableBitmap = bmp
            }

            buffer.rewind()
            bmp.copyPixelsFromBuffer(buffer)

            // A whole-display VirtualDisplay mirrors the navigation bar too. Drop it,
            // otherwise every frame carries the same translucent bar and the stitcher
            // repeats it throughout the long image. The same crop honours the user's
            // capture rectangle; single-app projection is left untouched.
            val fullDisplay = SystemBarCrop.isFullDisplayFrame(context, imgWidth, imgHeight)
            val region = if (fullDisplay) CaptureRegionStore.load(context) else null
            val left = (region?.left ?: 0).coerceIn(0, imgWidth - 1)
            val right = (region?.right ?: imgWidth).coerceIn(left + 1, imgWidth)
            val bandTop = (region?.top ?: 0).coerceIn(0, imgHeight - 1)
            val bandBottom = (region?.bottom ?: imgHeight).coerceIn(bandTop + 1, imgHeight)
            val top = when (captureSpan) {
                CaptureSpan.TOP_ONLY, CaptureSpan.FIRST -> 0
                CaptureSpan.BAND -> bandTop
                CaptureSpan.BOTTOM_ONLY -> bandBottom
            }
            val bottom = when (captureSpan) {
                CaptureSpan.TOP_ONLY -> bandTop
                CaptureSpan.BOTTOM_ONLY -> imgHeight
                CaptureSpan.BAND, CaptureSpan.FIRST -> bandBottom
            }
            val usableHeight = bottom - top

            val cleanPixels = IntArray(imgWidth * usableHeight)
            bmp.getPixels(cleanPixels, 0, imgWidth, 0, top, imgWidth, usableHeight)
            return PixelSlice(cleanPixels, right - left, usableHeight, stride = imgWidth, offset = left)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image buffer", e)
            return null
        } finally {
            image.close()
        }
    }

    override fun releaseSession() {
        captureSpan = CaptureSpan.BAND
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing VirtualDisplay", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ImageReader", e)
        }
        imageReader = null

        captureHandlerThread?.quitSafely()
        captureHandlerThread = null

        reusableBitmap?.recycle()
        reusableBitmap = null
        requestedSize = null
        sessionInvalid = false
        Log.i(TAG, "VirtualDisplay session released")
    }

    override fun release() {
        releaseSession()
    }

    companion object {
        private const val TAG = "MediaProjCapturer"
    }
}
