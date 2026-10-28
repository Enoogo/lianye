package org.lianye.engine

import org.lianye.engine.model.PixelSlice
import kotlin.math.abs

/** Position and temporal changes are separate: a playing tile does not move the surrounding list. */
internal class ManualFrameStability {
    fun hasStableLayout(first: PixelSlice, second: PixelSlice): Boolean {
        var evidence = 0
        var stable = 0f
        var stableMiddle = false
        for (row in 0 until ROWS) for (column in 0 until COLUMNS) {
            var features = 0
            var agreedFeatures = 0
            var error = 0
            for (sampleY in 0 until 8) for (sampleX in 0 until 8) {
                val x = ((column + (sampleX + 0.5f) / 8) * first.width / COLUMNS).toInt().coerceAtMost(first.width - 3)
                val y = ((row + (sampleY + 0.5f) / 8) * first.height / ROWS).toInt().coerceAtMost(first.height - 3)
                val a = first.getPixel(x, y); val b = second.getPixel(x, y)
                val difference = difference(a, b)
                error += minOf(32, difference)
                if (maxOf(difference(a, first.getPixel(x + 2, y)), difference(b, second.getPixel(x + 2, y)),
                        difference(a, first.getPixel(x, y + 2)), difference(b, second.getPixel(x, y + 2))) >= 8) {
                    features++
                    if (difference <= 6) agreedFeatures++
                }
            }
            if (features < 8 && error < 160) continue
            evidence++
            val colorAgreement = 1f - error / (64f * 32)
            val featureAgreement = if (features == 0) 1f else agreedFeatures.toFloat() / features
            val agreement = minOf(colorAgreement, featureAgreement)
            stable += agreement
            if (agreement > 0.2f && row in ROWS / 3 until ROWS * 2 / 3) stableMiddle = true
        }
        return evidence >= 6 && stable >= evidence * 0.70f && stableMiddle
    }

    /** Pixel masks use the analyzer's sampling geometry and mark spatial regions, not isolated noise. */
    fun changedRegions(first: PixelSlice, second: PixelSlice): BooleanArray {
        val width = first.width; val height = first.height
        val changed = IntArray(COLUMNS * ROWS)
        val sampled = IntArray(changed.size)
        val startX = (width * 0.08f).toInt()
        val endX = (width * 0.92f).toInt()
        val stepY = maxOf(1, height / 512)
        for (y in 0 until height step stepY) for (column in 0 until SAMPLE_COLUMNS) {
            val x = startX + (endX - startX - 1) * column / (SAMPLE_COLUMNS - 1)
            val block = minOf(ROWS - 1, y * ROWS / height) * COLUMNS + minOf(COLUMNS - 1, x * COLUMNS / width)
            sampled[block]++
            if (difference(first.getPixel(x, y), second.getPixel(x, y)) > 6) changed[block]++
        }
        return BooleanArray(height * SAMPLE_COLUMNS) { index ->
            val y = index / SAMPLE_COLUMNS
            val x = startX + (endX - startX - 1) * (index % SAMPLE_COLUMNS) / (SAMPLE_COLUMNS - 1)
            val block = minOf(ROWS - 1, y * ROWS / height) * COLUMNS + minOf(COLUMNS - 1, x * COLUMNS / width)
            changed[block] >= 4 && changed[block] >= sampled[block] * 0.12f
        }
    }

    private fun difference(a: Int, b: Int) = (abs((a ushr 16 and 255) - (b ushr 16 and 255)) +
        abs((a ushr 8 and 255) - (b ushr 8 and 255)) + abs((a and 255) - (b and 255))) / 3

    companion object {
        private const val COLUMNS = 6
        private const val ROWS = 16
        const val SAMPLE_COLUMNS = 32
    }
}
