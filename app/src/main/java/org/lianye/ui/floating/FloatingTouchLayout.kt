package org.lianye.ui.floating

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Draggable container for the floating control.
 *
 * Deliberately has no edge docking, no half-hidden state and no drop target:
 * the bubble is either fully on screen or removed. Dragging is clamped to a
 * safety margin so it can never sit inside the system back-gesture strips.
 */
class FloatingTouchLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var capturePixelsHidden = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    override fun dispatchDraw(canvas: Canvas) {
        // Root View.alpha is not applied by every software window drawing path.
        // Keep layout and input intact, while emitting a genuinely empty surface.
        if (!capturePixelsHidden) super.dispatchDraw(canvas)
    }

    var windowManager: WindowManager? = null
    var windowParams: WindowManager.LayoutParams? = null
    var onPositionChanged: ((Int, Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocityTracker: VelocityTracker? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Keeps the control at least 50 physical pixels away from every screen edge. */
    private val edgeMarginPx = 50

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                initialX = windowParams?.x ?: 0
                initialY = windowParams?.y ?: 0
                isDragging = false

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    isDragging = true
                    // 向子 View 派发 ACTION_CANCEL，取消其内部按压态
                    val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)
        val params = windowParams ?: return super.onTouchEvent(ev)
        val wm = windowManager ?: return super.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val dx = (ev.rawX - initialTouchX).toInt()
                val dy = (ev.rawY - initialTouchY).toInt()

                val screenBounds = getScreenBounds()
                val newX = (initialX + dx).coerceIn(
                    screenBounds.leftInset,
                    maxOf(screenBounds.leftInset, screenBounds.width - width - screenBounds.rightInset)
                )
                val newY = (initialY + dy).coerceIn(
                    screenBounds.topInset,
                    maxOf(screenBounds.topInset, screenBounds.height - height - screenBounds.bottomInset)
                )

                params.x = newX
                params.y = newY
                if (isAttachedToWindow) {
                    wm.updateViewLayout(this, params)
                }
                onPositionChanged?.invoke(newX, newY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null

                if (isDragging) {
                    isDragging = false
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPositionChanged?.invoke(params.x, params.y)
                } else {
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun clampPosition() {
        val params = windowParams ?: return
        val bounds = getScreenBounds()
        params.x = params.x.coerceIn(bounds.leftInset, maxOf(bounds.leftInset, bounds.width - width - bounds.rightInset))
        params.y = params.y.coerceIn(bounds.topInset, maxOf(bounds.topInset, bounds.height - height - bounds.bottomInset))
        if (isAttachedToWindow) windowManager?.updateViewLayout(this, params)
    }

    fun centerOnScreen() {
        val params = windowParams ?: return
        val bounds = getScreenBounds()
        params.x = ((bounds.width - width) / 2).coerceAtLeast(bounds.leftInset)
        params.y = ((bounds.height - height) / 2).coerceAtLeast(bounds.topInset)
        if (isAttachedToWindow) windowManager?.updateViewLayout(this, params)
        onPositionChanged?.invoke(params.x, params.y)
    }

    private data class ScreenBounds(
        val width: Int,
        val height: Int,
        val topInset: Int,
        val bottomInset: Int,
        val leftInset: Int,
        val rightInset: Int
    )

    private fun getScreenBounds(): ScreenBounds {
        val dm = resources.displayMetrics
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) windowManager?.currentWindowMetrics?.bounds else null
        val width = bounds?.width() ?: dm.widthPixels
        val height = bounds?.height() ?: dm.heightPixels
        val insets = if (android.os.Build.VERSION.SDK_INT >= 30) {
            windowManager?.currentWindowMetrics?.windowInsets
                ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
        } else null
        @Suppress("DEPRECATION")
        val systemTop = insets?.top ?: rootWindowInsets?.systemWindowInsetTop ?: 0
        @Suppress("DEPRECATION")
        val systemBottom = insets?.bottom ?: rootWindowInsets?.systemWindowInsetBottom ?: 0
        @Suppress("DEPRECATION")
        val systemLeft = insets?.left ?: rootWindowInsets?.systemWindowInsetLeft ?: 0
        @Suppress("DEPRECATION")
        val systemRight = insets?.right ?: rootWindowInsets?.systemWindowInsetRight ?: 0
        return ScreenBounds(
            width,
            height,
            maxOf(systemTop, rootWindowInsets?.displayCutout?.safeInsetTop ?: 0) + edgeMarginPx,
            systemBottom + edgeMarginPx,
            maxOf(systemLeft, rootWindowInsets?.displayCutout?.safeInsetLeft ?: 0) + edgeMarginPx,
            maxOf(systemRight, rootWindowInsets?.displayCutout?.safeInsetRight ?: 0) + edgeMarginPx
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacksAndMessages(null)
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
