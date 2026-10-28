package org.lianye.platform

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

/**
 * Physical display size in pixels, with (0,0) at the real top-left corner of the panel —
 * including the status bar and any cutout area. Window metrics from an app context can be
 * inset by the system bars, which is not acceptable for capture-region coordinates.
 */
object ScreenGeometry {

    fun displaySize(context: Context): Point {
        val metrics = DisplayMetrics()
        val manager = context.getSystemService(DisplayManager::class.java)
        val display = manager?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                return Point(metrics.widthPixels, metrics.heightPixels)
            }
        }
        val resources = context.resources.displayMetrics
        return Point(resources.widthPixels, resources.heightPixels)
    }
}
