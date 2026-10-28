package org.lianye.service.capture

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Resolves the height of the on-screen navigation bar so captured frames can exclude it.
 *
 * Frames come from display-level capture APIs (AccessibilityService#takeScreenshot and
 * MediaProjection), both of which include the navigation bar. Because that bar is
 * translucent on gesture-navigation devices, the stitcher sees a repeated strip of
 * pixels (the gesture pill) that never scrolls, and it ends up woven into the output.
 * Cropping the bar at capture time keeps the seam detector working on real content only.
 */
internal object SystemBarCrop {

    private const val TAG = "SystemBarCrop"

    /** Height in pixels of the visible navigation bar, or 0 when it is hidden or unknown. */
    fun navigationBarHeightPx(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            try {
                val insets = wm.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                if (insets.bottom > 0) return insets.bottom
                // A visible-insets result of 0 means the bar is genuinely hidden
                // (immersive capture), so nothing should be cropped.
                return 0
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to read window insets, falling back to resources", error)
            }
        }
        val resources = context.resources
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) {
            val px = resources.getDimensionPixelSize(id)
            if (px > 0) return px
        }
        return 0
    }

    /** True when the frame is a full-display frame that includes the system bars. */
    fun isFullDisplayFrame(context: Context, width: Int, height: Int): Boolean {
        val size = org.lianye.platform.ScreenGeometry.displaySize(context)
        return width == size.x && height == size.y
    }
}
