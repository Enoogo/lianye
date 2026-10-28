package org.lianye.platform

import android.content.Context

/**
 * Per-edge snap behaviour for the capture-region editor.
 *
 * Snap is only consulted while an edge is being dragged: enabling it never moves an edge by
 * itself, and a drag that ends within [threshold] pixels of the screen edge lands exactly on it.
 */
data class EdgeSnap(
    val enabled: Boolean = false,
    val threshold: Int = RegionSnapSettings.DEFAULT_THRESHOLD
)

object RegionSnapSettings {

    const val DEFAULT_THRESHOLD = 30
    private const val PREFS = "region_snap"

    enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    fun load(context: Context): Map<Edge, EdgeSnap> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Edge.entries.associateWith { edge ->
            EdgeSnap(
                enabled = prefs.getBoolean("${edge.name}_enabled", false),
                threshold = prefs.getInt("${edge.name}_threshold", DEFAULT_THRESHOLD)
                    .coerceIn(0, 400)
            )
        }
    }

    fun save(context: Context, values: Map<Edge, EdgeSnap>) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        values.forEach { (edge, snap) ->
            editor.putBoolean("${edge.name}_enabled", snap.enabled)
            editor.putInt("${edge.name}_threshold", snap.threshold.coerceIn(0, 400))
        }
        editor.apply()
    }
}
