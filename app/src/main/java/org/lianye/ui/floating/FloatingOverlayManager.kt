package org.lianye.ui.floating

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lianye.domain.model.WeavingState
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualPhase
import org.lianye.domain.repository.LianyeRepository
import org.lianye.ui.common.theme.LianyeTheme

class FloatingOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val repository: LianyeRepository,
    private val scope: CoroutineScope,
    private val onStartCapture: () -> Unit,
    private val onStopCapture: () -> Unit,
    private val onOpenPreview: () -> Unit,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    private val onPositionChanged: () -> Unit = {}
) {
    private var touchLayout: FloatingTouchLayout? = null
    private var composeView: ComposeView? = null
    private var lifecycleBridge: FloatingLifecycleBridge? = null
    @Volatile
    private var isAttached = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = context.getSharedPreferences("overlay", Context.MODE_PRIVATE)
    private var previewVisible = false
    private var captureHidden = false
    private var stopping by mutableStateOf(false)
    private var manualState by mutableStateOf<ManualCaptureState?>(null)
    val hasAttachedWindows: Boolean get() = isAttached
    val controlBounds: Rect? get() = touchLayout?.let {
        Rect(windowParams.x, windowParams.y, windowParams.x + it.width, windowParams.y + it.height)
    }

    private val windowParams = WindowManager.LayoutParams().apply {
        type = windowType
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        gravity = Gravity.TOP or Gravity.START
        // Real coordinates are resolved after the first layout pass: centre on first run,
        // otherwise restore the last user-chosen spot.
        x = 0
        y = 0
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    @MainThread
    fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show() }
            return
        }
        if (isAttached) {
            updateVisibility()
            return
        }

        val bridge = FloatingLifecycleBridge()
        lifecycleBridge = bridge

        val layout = FloatingTouchLayout(context).apply {
            this.windowManager = this@FloatingOverlayManager.windowManager
            this.windowParams = this@FloatingOverlayManager.windowParams
            onPositionChanged = { x, y ->
                preferences.edit()
                    .putBoolean("has_pos", true)
                    .putInt("pos_x", x)
                    .putInt("pos_y", y)
                    .apply()
                this@FloatingOverlayManager.onPositionChanged()
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> this@FloatingOverlayManager.onPositionChanged() }
        }

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LianyeTheme {
                    val state by repository.weavingState.collectAsState()
                    val draft by repository.draft.collectAsState()
                    LaunchedEffect(state, manualState) {
                        val activeManual = manualState?.phase in listOf(ManualPhase.TAKING_FIRST, ManualPhase.RECORDING, ManualPhase.GAP, ManualPhase.FINISHING)
                        if (state !is WeavingState.Weaving && !activeManual) stopping = false
                        updateVisibility()
                    }

                    run {
                        LianyeFloatingBubble(
                            state = state,
                            onStartClick = onStartCapture,
                            onStopClick = {
                                stopping = true
                                onStopCapture()
                            },
                            onPreviewClick = onOpenPreview,
                            onCloseRequest = { dismissOverlay() },
                            hasDraft = draft?.isSaved == false,
                            isStopping = stopping,
                            manualSession = manualState
                        )
                    }
                }
            }
        }

        layout.addView(view)
        layout.capturePixelsHidden = captureHidden
        layout.alpha = if (captureHidden) 0f else 1f
        bridge.attach(root = layout, child = view)
        windowManager.addView(layout, windowParams)
        touchLayout = layout
        composeView = view
        isAttached = true
        repository.setOverlayVisible(true)
        layout.post {
            if (preferences.getBoolean("has_pos", false)) {
                windowParams.x = preferences.getInt("pos_x", windowParams.x)
                windowParams.y = preferences.getInt("pos_y", windowParams.y)
                layout.clampPosition()
            } else {
                layout.centerOnScreen()
            }
            updateVisibility()
        }
    }

    fun setPreviewVisible(visible: Boolean) {
        previewVisible = visible
        mainHandler.post { updateVisibility() }
    }

    @MainThread
    fun updateManualState(state: ManualCaptureState) {
        manualState = state
        val hasNotice = state.message == "temporarily_unmatched" || state.phase == ManualPhase.GAP || state.phase == ManualPhase.FIRST_FAILED
        // Ask WindowManager for the full notice width before Compose measures it.
        // WRAP_CONTENT at the old narrow button's x can clip a newly widened notice.
        val noticeWidthDp = if (state.guide == org.lianye.domain.model.ManualGuide.RECOVERY) 104 else 176
        val width = if (hasNotice) minOf((noticeWidthDp * context.resources.displayMetrics.density).toInt(),
            context.resources.displayMetrics.widthPixels) else WindowManager.LayoutParams.WRAP_CONTENT
        if (windowParams.width != width) {
            windowParams.width = width
            touchLayout?.takeIf { isAttached && it.isAttachedToWindow }?.let {
                windowManager.updateViewLayout(it, windowParams)
                it.clampPosition()
            }
        }
    }

    private fun updateVisibility() {
        touchLayout?.apply {
            visibility = if (previewVisible) View.GONE else View.VISIBLE
            capturePixelsHidden = captureHidden
            alpha = if (captureHidden) 0f else 1f
        }
    }

    /**
     * Blanks the overlay pixels for the duration of one captured frame, so the Finish control
     * never appears in the stitched image. The window stays attached and input-capable — only
     * its drawing is suppressed. [showAfterCapture] brings the control back right after the
     * frame is taken, so the user can tap Finish between frames.
     *
     * (FLAG_SECURE was tried first and rejected: AccessibilityService.takeScreenshot on Pixel
     * still captures secure windows, and a visible control acts as a static matching anchor
     * that corrupts the overlap deltas.)
     *
     * @return the frame timestamp to wait for before capturing, or null if no fence is available.
     */
    suspend fun hideBeforeCapture(): Long? = withContext(Dispatchers.Main.immediate) {
        captureHidden = true
        val layout = touchLayout ?: return@withContext 0L
        updateVisibility()
        // A transparent window retains Finish input; an invisible window would pass it through.
        windowParams.alpha = 1f
        if (isAttached && layout.isAttachedToWindow) windowManager.updateViewLayout(layout, windowParams)
        CaptureWindowFrameFence.awaitHiddenFrames(layout)
    }

    @MainThread
    fun showAfterCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showAfterCapture() }
            return
        }
        captureHidden = false
        val layout = touchLayout ?: return
        updateVisibility()
        windowParams.alpha = 1.0f
        if (isAttached && layout.isAttachedToWindow) {
            windowManager.updateViewLayout(layout, windowParams)
        }
    }

    /** Removes the bubble because the user is done, and keeps it hidden until they ask again. */
    @MainThread
    fun dismissOverlay() {
        if (preferences.getBoolean("haptics_enabled", true)) {
            touchLayout?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        preferences.edit().putBoolean("enabled", false).apply()
        // Called from the bubble's own touch dispatch when dropped on the target;
        // defer removal so the view is never detached mid-gesture.
        mainHandler.post { hide() }
    }

    @MainThread
    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        if (!isAttached) return
        val layout = touchLayout ?: return
        val view = composeView
        if (view != null) {
            lifecycleBridge?.detach(root = layout, composeView = view)
        }
        lifecycleBridge = null
        try {
            windowManager.removeView(layout)
        } catch (e: Exception) {
            // Safe removal
        }
        touchLayout = null
        composeView = null
        isAttached = false
        captureHidden = false
        repository.setOverlayVisible(false)
    }
}
