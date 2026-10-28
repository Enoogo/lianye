package org.lianye.platform

import android.content.Context
import android.graphics.Rect
import org.lianye.service.capture.SystemBarCrop

/**
 * Persistent capture rectangle, in display pixels with the top-left of the screen as origin.
 *
 * Captures are always full-display frames, so the region is applied at capture time and
 * therefore applies identically to automatic and manual sessions. The default is the whole
 * screen minus the navigation bar, which is also what the navigation-bar fix crops.
 */
object CaptureRegionStore {

    private const val PREFS = "capture_region"
    private const val KEY_SET = "set"
    private const val KEY_LEFT = "left"
    private const val KEY_TOP = "top"
    private const val KEY_RIGHT = "right"
    private const val KEY_BOTTOM = "bottom"
    private const val KEY_FULL_HEIGHT = "full_height"

    const val MIN_SIZE_PX = 48

    /** Whole physical display: the draggable limit for every edge. */
    fun fullFrame(context: Context): Rect {
        val size = ScreenGeometry.displaySize(context)
        return Rect(0, 0, size.x, size.y)
    }

    /** Default rectangle: whole screen except the navigation bar. */
    fun defaultFrame(context: Context): Rect {
        val size = ScreenGeometry.displaySize(context)
        val navBar = SystemBarCrop.navigationBarHeightPx(context)
        return Rect(0, 0, size.x, (size.y - navBar).coerceAtLeast(MIN_SIZE_PX))
    }

    fun load(context: Context): Rect {
        val defaults = defaultFrame(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SET, false)) return defaults
        val stored = Rect(
            prefs.getInt(KEY_LEFT, defaults.left),
            prefs.getInt(KEY_TOP, defaults.top),
            prefs.getInt(KEY_RIGHT, defaults.right),
            prefs.getInt(KEY_BOTTOM, defaults.bottom)
        )
        return clamp(stored, fullFrame(context))
    }

    fun save(context: Context, rect: Rect) {
        val clamped = clamp(rect, fullFrame(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SET, true)
            .putInt(KEY_LEFT, clamped.left)
            .putInt(KEY_TOP, clamped.top)
            .putInt(KEY_RIGHT, clamped.right)
            .putInt(KEY_BOTTOM, clamped.bottom)
            .apply()
    }

    fun isFullHeightMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FULL_HEIGHT, false)

    fun setFullHeightMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FULL_HEIGHT, enabled).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Keeps the rectangle on screen and at least [MIN_SIZE_PX] wide/tall. */
    fun clamp(rect: Rect, frame: Rect): Rect {
        val minSize = MIN_SIZE_PX.coerceAtMost(minOf(frame.width(), frame.height()))
        var left = rect.left.coerceIn(frame.left, frame.right - minSize)
        var top = rect.top.coerceIn(frame.top, frame.bottom - minSize)
        var right = rect.right.coerceIn(left + minSize, frame.right)
        var bottom = rect.bottom.coerceIn(top + minSize, frame.bottom)
        if (bottom > frame.bottom) {
            bottom = frame.bottom
            top = (bottom - minSize).coerceAtLeast(frame.top)
        }
        if (right > frame.right) {
            right = frame.right
            left = (right - minSize).coerceAtLeast(frame.left)
        }
        return Rect(left, top, right, bottom)
    }

    fun describe(context: Context): String {
        val region = load(context)
        return "TL (${region.left}, ${region.top})  BR (${region.right}, ${region.bottom})"
    }
}
