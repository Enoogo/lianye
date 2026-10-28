package org.lianye.ui.floating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualControl
import org.lianye.domain.model.ManualGuide
import org.lianye.domain.model.ManualPhase
import org.lianye.domain.repository.LianyeRepository
import org.lianye.ui.common.localized

/** Ordinary application windows. This class never requires or dispatches accessibility gestures. */
class ManualFloatingOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    repository: LianyeRepository,
    scope: CoroutineScope,
    private val onStart: () -> Unit,
    private val onFinish: () -> Unit,
    private val onUnavailable: () -> Unit
) {
    private data class Window(val view: View, val params: WindowManager.LayoutParams, val visibleAlpha: Float = params.alpha)
    private val windows = mutableListOf<Window>()
    @Volatile private var attachedWindowCount = 0
    val hasAttachedWindows: Boolean get() = attachedWindowCount > 0 || controls.hasAttachedWindows
    @Volatile var hasEverAttachedWindows = false
        private set
    private val density = context.resources.displayMetrics.density
    private val dark = context.resources.configuration.uiMode and 0x30 == 0x20
    private val orange = Color.parseColor(if (dark) "#F4A584" else "#DB653D")
    private var state = ManualCaptureState()
    private var productVisible = true
    private var captureHidden = false
    private val controls = FloatingOverlayManager(context, windowManager, repository, scope,
        onStartCapture = onStart, onStopCapture = onFinish, onOpenPreview = {},
        windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        onPositionChanged = { positionDecorations() })
    private var guideWindow: Window? = null
    private var guideOnRight = !context.getSharedPreferences("overlay", Context.MODE_PRIVATE).getBoolean("right", true)
    val controlBounds get() = controls.controlBounds

    private fun dp(value: Float) = (value * density).toInt()
    private fun text(zh: String, en: String) = localized(zh, en)

    private fun params(width: Int, height: Int, touchable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            if (!touchable) {
                val limit = if (Build.VERSION.SDK_INT >= 31)
                    context.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch ?: 0.8f
                else 0.8f
                // Pixel alpha is insufficient on Android 12: the WINDOW alpha must be below the limit.
                alpha = minOf(0.72f, limit)
            }
        }

    private fun add(view: View, params: WindowManager.LayoutParams): Window? = try {
        val window = Window(view, params)
        applyVisibility(window)
        windowManager.addView(view, params)
        window.also {
            windows.add(it)
            hasEverAttachedWindows = true
            attachedWindowCount = windows.size
        }
    } catch (_: RuntimeException) {
        hide()
        onUnavailable()
        null
    }

    fun render(updated: ManualCaptureState) {
        state = updated
        if (productVisible || updated.control != ManualControl.OVERLAY || updated.phase == ManualPhase.IDLE) {
            hide()
            return
        }
        controls.updateManualState(updated)
        if (!controls.hasAttachedWindows) {
            try { controls.show(); hasEverAttachedWindows = true }
            catch (_: RuntimeException) { hide(); onUnavailable(); return }
        }
        updateGuide()
        positionDecorations()
        applyVisibility()
    }

    private fun updateGuide() {
        val needed = state.phase == ManualPhase.RECORDING && state.guide != ManualGuide.HIDDEN
        if (!needed) {
            remove(guideWindow); guideWindow = null
            return
        }
        if (guideWindow == null) {
            guideWindow = add(SwipeGuideView(context).apply { onRight = guideOnRight },
                params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false))
        }
        (guideWindow?.view as? SwipeGuideView)?.apply {
            guide = state.guide
            contentDescription = if (guide == ManualGuide.RECOVERY)
                text("向下滑回一点，松手稍停", "Swipe down a little, then release and pause")
            else text("从这里向上滑，松手稍停", "Swipe up here, then release and pause")
            invalidate()
        }
    }

    private fun positionDecorations() {
        val bounds = controls.controlBounds ?: return
        val dm = context.resources.displayMetrics
        val right = bounds.centerX() >= dm.widthPixels / 2
        if (guideOnRight == right) {
            guideOnRight = !right
            (guideWindow?.view as? SwipeGuideView)?.onRight = guideOnRight
            guideWindow?.view?.invalidate()
        }
    }

    fun setProductVisible(visible: Boolean) {
        productVisible = visible
        render(state)
    }

    suspend fun hideBeforeCapture() = withContext(Dispatchers.Main.immediate) {
        captureHidden = true
        applyVisibility()
        controls.hideBeforeCapture()
    }

    fun showAfterCapture() {
        captureHidden = false
        controls.showAfterCapture()
        applyVisibility()
    }

    private fun applyVisibility() {
        windows.forEach { window ->
            applyVisibility(window)
            runCatching { windowManager.updateViewLayout(window.view, window.params) }
        }
    }

    private fun applyVisibility(window: Window) {
        window.view.visibility = if (productVisible) View.INVISIBLE else View.VISIBLE
        window.view.alpha = if (captureHidden) 0f else 1f
        val touchable = window.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
        window.params.alpha = if (captureHidden && !touchable) 0f else window.visibleAlpha
    }

    private fun remove(window: Window?) {
        if (window == null) return
        windows.remove(window)
        runCatching { windowManager.removeViewImmediate(window.view) }
        attachedWindowCount = windows.size
    }

    fun hide() {
        controls.hide()
        windows.toList().forEach(::remove)
        guideWindow = null
    }

    private inner class SwipeGuideView(context: Context) : View(context) {
        var guide = ManualGuide.FULL
        var onRight = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arrow = Path()
        private val strokeColors = intArrayOf(Color.rgb(28, 32, 31), Color.WHITE, orange)
        private val strokeWidths = floatArrayOf(3.5f, 2.5f, 1.5f)
        override fun onDraw(canvas: Canvas) {
            val x = width * if (onRight) 0.72f else 0.28f
            val recovering = guide == ManualGuide.RECOVERY
            // Recovery suggests a small step, not an exact distance to the saved anchor.
            // Ordinary capture leaves extra overlap for inertial scrolling after release.
            val top = maxOf(dp(100f).toFloat(), height * if (recovering) 0.44f else 0.37f)
            val bottom = minOf(height - dp(110f).toFloat(), height * 0.74f,
                if (recovering) top + dp(160f) else Float.MAX_VALUE)
            val start = if (recovering) top else bottom
            val end = if (recovering) bottom else top
            val direction = if (recovering) -1 else 1
            paint.style = Paint.Style.STROKE
            paint.alpha = 255
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            arrow.reset()
            arrow.moveTo(x - dp(8f), end + direction * dp(29f))
            arrow.lineTo(x, end + direction * dp(18f))
            arrow.lineTo(x + dp(8f), end + direction * dp(29f))
            // Both light and dark edges remain distinguishable over arbitrary page colors.
            for (index in strokeColors.indices) {
                paint.color = strokeColors[index]
                paint.strokeWidth = strokeWidths[index] * density
                canvas.drawLine(x, bottom - dp(18f), x, top + dp(18f), paint)
                canvas.drawPath(arrow, paint)
                canvas.drawCircle(x, end, dp(5f).toFloat(), paint)
                canvas.drawCircle(x, start, dp(7f).toFloat(), paint)
            }
            if (recovering) {
                label(canvas, x, top - dp(22f), text("向下滑回一点", "Swipe down a little"))
                label(canvas, x, bottom + dp(29f), text("松手，稍停", "Release; pause"))
            } else if (guide == ManualGuide.FULL) {
                label(canvas, x, top - dp(22f), text("松手，稍停", "Release; pause"))
                label(canvas, x, bottom + dp(29f), text("从这里向上滑", "Swipe up here"))
            }
        }
        private fun label(canvas: Canvas, x: Float, y: Float, label: String) {
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            paint.textSize = 12f * density
            paint.textAlign = Paint.Align.CENTER
            val textWidth = paint.measureText(label)
            val center = x.coerceIn(textWidth / 2 + dp(18f), maxOf(textWidth / 2 + dp(18f), width - textWidth / 2 - dp(18f)))
            paint.color = Color.rgb(28, 32, 31)
            canvas.drawRoundRect(center - textWidth / 2 - dp(10f), y - dp(17f), center + textWidth / 2 + dp(10f), y + dp(7f), dp(8f).toFloat(), dp(8f).toFloat(), paint)
            paint.color = Color.WHITE
            canvas.drawText(label, center, y, paint)
        }
    }
}
