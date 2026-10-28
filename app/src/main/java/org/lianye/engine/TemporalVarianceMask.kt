package org.lianye.engine

import org.lianye.engine.model.PixelSlice

class TemporalVarianceMask(
    val varianceThreshold: Long = 16L
) {
    /**
     * Given 3 consecutive frames, computes a boolean mask of height H:
     * mask[y] is true if row y is dynamic (moving), false if row y is static (Sticky Header / TabBar).
     */
    fun computeRowMask(slice1: PixelSlice, slice2: PixelSlice, slice3: PixelSlice): BooleanArray {
        val h = minOf(slice1.height, slice2.height, slice3.height)
        val w = minOf(slice1.width, slice2.width, slice3.width)
        val mask = BooleanArray(h) { true }

        val startX = (w * 0.05f).toInt()
        val endX = (w * 0.90f).toInt()
        val count = endX - startX
        if (count <= 0) return mask

        for (y in 0 until h) {
            var diff12Sum = 0L
            var diff23Sum = 0L

            for (x in startX until endX) {
                val p1 = slice1.getPixel(x, y)
                val p2 = slice2.getPixel(x, y)
                val p3 = slice3.getPixel(x, y)

                val y1 = ((77 * ((p1 ushr 16) and 0xFF) + 150 * ((p1 ushr 8) and 0xFF) + 29 * (p1 and 0xFF)) ushr 8)
                val y2 = ((77 * ((p2 ushr 16) and 0xFF) + 150 * ((p2 ushr 8) and 0xFF) + 29 * (p2 and 0xFF)) ushr 8)
                val y3 = ((77 * ((p3 ushr 16) and 0xFF) + 150 * ((p3 ushr 8) and 0xFF) + 29 * (p3 and 0xFF)) ushr 8)

                diff12Sum += (y1 - y2) * (y1 - y2)
                diff23Sum += (y2 - y3) * (y2 - y3)
            }

            val meanDiff12 = diff12Sum / count
            val meanDiff23 = diff23Sum / count

            if (meanDiff12 <= varianceThreshold && meanDiff23 <= varianceThreshold) {
                mask[y] = false
            }
        }

        return mask
    }
}
