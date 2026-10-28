package org.lianye.engine

import org.lianye.engine.model.PixelSlice
import kotlin.math.abs

/** Conservative image-only decisions; no gesture prior or accessibility state is needed. */
internal class ManualFrameAnalyzer(private val matcher: OverlapMatcher) {
    var lastDecisionDetails: String = ""
        private set
    var scrollingBottom: Int = 0
        private set
    private var residuals = IntArray(0)
    private var changedRuns = IntArray(0)
    private var dynamicPixels = BooleanArray(0)
    private val stability = ManualFrameStability()
    private var earlierChanges: BooleanArray? = null
    private var laterChanges: BooleanArray? = null
    sealed interface Motion {
        data object Stationary : Motion
        data object Reverse : Motion
        data class Forward(val deltaY: Int) : Motion
        data object Unmatched : Motion
        data object SecureBlocked : Motion
    }

    fun samePosition(first: PixelSlice, second: PixelSlice): Boolean =
        sameGeometry(first, second) && (difference(first, second) <= SAME_POSITION_DIFFERENCE ||
            (first.height >= 3 && stability.hasStableLayout(first, second)))

    fun changedRegions(first: PixelSlice, second: PixelSlice): BooleanArray = stability.changedRegions(first, second)

    fun analyze(anchor: PixelSlice, candidate: PixelSlice,
                anchorChanges: BooleanArray? = null, candidateChanges: BooleanArray? = null): Motion {
        lastDecisionDetails = ""
        scrollingBottom = candidate.height
        if (!sameGeometry(anchor, candidate)) return Motion.Unmatched
        if (isSecureBlocked(candidate)) return Motion.SecureBlocked
        // A repeated periodic list must never be advanced by the previous gesture distance.
        if (samePosition(anchor, candidate)) return Motion.Stationary
        val first = sampledColumns(anchor)
        val second = sampledColumns(candidate)
        if (unchangedOutsideTemporalRegions(first, second, anchor.height, anchorChanges, candidateChanges)) {
            lastDecisionDetails = "stationary outside observed animation"
            return Motion.Stationary
        }
        val capacity = anchor.height * ALIGNMENT_COLUMNS
        if (residuals.size < capacity) {
            residuals = IntArray(capacity)
            changedRuns = IntArray(capacity)
            dynamicPixels = BooleanArray(capacity)
        }
        // Same-position edge chrome is outside the scrolling document. Its measured size,
        // rather than a fixed percentage, defines the shared content interval.
        val top = fixedEdge(first, second, anchor.height, fromBottom = false)
        val bottom = anchor.height - fixedEdge(first, second, anchor.height, fromBottom = true)
        val minimumOverlap = maxOf(24, anchor.height / 10)
        val range = minOf(searchRange(anchor), bottom - top - minimumOverlap)
        if (range < 1) return Motion.Unmatched
        val scores = FloatArray(range * 2 + 1) { Float.POSITIVE_INFINITY }
        var bestShift = 0
        var bestError = Float.POSITIVE_INFINITY
        for (shift in -range..range) {
            if (shift == 0) continue
            earlierChanges = if (shift > 0) anchorChanges else candidateChanges
            laterChanges = if (shift > 0) candidateChanges else anchorChanges
            val error = if (shift > 0) alignmentDifference(first, second, top, bottom, shift)
                else alignmentDifference(second, first, top, bottom, -shift)
            scores[shift + range] = error
            if (error < bestError) { bestError = error; bestShift = shift }
        }
        lastDecisionDetails = "region=$top..$bottom best=$bestShift/$bestError"
        if (bestError > MAX_ALIGNMENT_DIFFERENCE) return Motion.Unmatched
        val suppression = maxOf(6, (8f * matcher.screenDensity).toInt())
        var alternative = Float.POSITIVE_INFINITY
        for (shift in -range..range) {
            if (abs(shift - bestShift) > suppression) alternative = minOf(alternative, scores[shift + range])
        }
        lastDecisionDetails += " alternative=$alternative"
        // Similar peaks are ambiguous. Without a gesture distance prior, never guess a seam.
        if ((bestError + 0.05f) / (alternative + 0.05f) >= 0.85f) return Motion.Unmatched
        // Rebuild the mask for the chosen seam, then inspect fine local features. A high
        // whole-viewport agreement cannot overrule a contradictory section number/title.
        val earlier = if (bestShift > 0) anchor else candidate
        val later = if (bestShift > 0) candidate else anchor
        val earlierSamples = if (bestShift > 0) first else second
        val laterSamples = if (bestShift > 0) second else first
        earlierChanges = if (bestShift > 0) anchorChanges else candidateChanges
        laterChanges = if (bestShift > 0) candidateChanges else anchorChanges
        val (contentTop, contentBottom) = scrollingOverlap(earlierSamples, laterSamples,
            anchor.height, top, bottom, abs(bestShift))
        if (alignmentDifference(earlierSamples, laterSamples, contentTop, contentBottom, abs(bestShift)) > MAX_ALIGNMENT_DIFFERENCE)
            return Motion.Unmatched
        lastDecisionDetails += " content=$contentTop..$contentBottom"
        if (!consistentStableFeatures(earlier, later, contentTop, contentBottom, abs(bestShift))) {
            lastDecisionDetails += " conflicting local features"
            return Motion.Unmatched
        }
        scrollingBottom = contentBottom
        return if (bestShift < 0) Motion.Reverse else Motion.Forward(bestShift)
    }

    /** Collapsing headers/translucent footers need evidence at the chosen shifted position. */
    private fun scrollingOverlap(first: IntArray, second: IntArray, height: Int,
                                 top: Int, bottom: Int, displacement: Int): Pair<Int, Int> {
        var firstContent = -1
        var lastContent = -1
        for (y in top + displacement until bottom) {
            val a = y * ALIGNMENT_COLUMNS
            val b = (y - displacement) * ALIGNMENT_COLUMNS
            var features = 0
            var agreed = 0
            for (x in 0 until ALIGNMENT_COLUMNS - 1) {
                if (maxOf(colorDifference(first[a + x], first[a + x + 1]),
                        colorDifference(second[b + x], second[b + x + 1])) < MIN_FEATURE_CONTRAST) continue
                features++
                if (colorDifference(first[a + x], second[b + x]) <= PIXEL_AGREEMENT) agreed++
            }
            if (features >= 4 && agreed >= features * 0.90f) {
                if (firstContent < 0) firstContent = y
                lastContent = y
            }
        }
        // Only edge chrome can be excluded this way. Interior contradictions remain visible
        // to local validation, including distinctive headings in otherwise repeated prose.
        val refinedTop = if (firstContent >= 0 && firstContent - displacement < height * 0.4f)
            maxOf(top, firstContent - displacement) else top
        val refinedBottom = if (lastContent >= height * 0.6f)
            minOf(bottom, lastContent + 1) else bottom
        return if (refinedBottom - refinedTop - displacement >= maxOf(24, height / 10))
            refinedTop to refinedBottom else top to bottom
    }

    private fun consistentStableFeatures(first: PixelSlice, second: PixelSlice,
                                         top: Int, bottom: Int, displacement: Int): Boolean {
        val startY = top + displacement
        val startX = (first.width * 0.08f).toInt()
        val endX = (first.width * 0.92f).toInt()
        val blockWidth = maxOf(24, (32 * matcher.screenDensity).toInt())
        val blockHeight = maxOf(12, (12 * matcher.screenDensity).toInt())
        val blocksX = (endX - startX + blockWidth - 1) / blockWidth
        val blocksY = (bottom - startY + blockHeight - 1) / blockHeight
        val features = IntArray(blocksX * blocksY)
        val conflicts = IntArray(features.size)
        val sampledStepY = maxOf(1, (bottom - top) / 256)
        for (y in startY until bottom - 2 step 2) {
            val row = (y - startY) / sampledStepY
            for (x in startX until endX - 2 step 4) {
                val column = ((x - startX).toFloat() * (ALIGNMENT_COLUMNS - 1) / (endX - startX - 1)).toInt()
                if (dynamicPixels[row * ALIGNMENT_COLUMNS + column]) continue
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y - displacement)
                val contrast = maxOf(colorDifference(a, first.getPixel(x + 2, y)),
                    colorDifference(b, second.getPixel(x + 2, y - displacement)),
                    colorDifference(a, first.getPixel(x, y + 2)),
                    colorDifference(b, second.getPixel(x, y - displacement + 2)))
                if (contrast < MIN_FEATURE_CONTRAST) continue
                val block = ((y - startY) / blockHeight) * blocksX + (x - startX) / blockWidth
                features[block]++
                if (colorDifference(a, b) > 12) conflicts[block]++
            }
        }
        for (block in features.indices) {
            if (features[block] >= 12 && conflicts[block] >= 4 && conflicts[block] > features[block] * 0.20f) {
                lastDecisionDetails += " conflict=${startX + block % blocksX * blockWidth},${startY + block / blocksX * blockHeight} ${conflicts[block]}/${features[block]}"
                return false
            }
        }
        return true
    }

    fun isSecureBlocked(frame: PixelSlice): Boolean =
        frame.height > 0 && matcher.isFrameAllBlack(frame)

    /** A scene cut can exceed layout stability's viewport threshold without moving the page.
     * Only observed temporal regions are ignored; every other sample must still agree at
     * zero displacement. Distributed interior features prevent static chrome alone proving this.
     */
    private fun unchangedOutsideTemporalRegions(first: IntArray, second: IntArray, height: Int,
                                                 firstChanges: BooleanArray?, secondChanges: BooleanArray?): Boolean {
        if (firstChanges == null && secondChanges == null) return false
        val step = maxOf(1, height / 256)
        var samples = 0
        var retained = 0
        var excluded = 0
        var features = 0
        var middleFeatures = 0
        val featureBands = BooleanArray(16)
        for (y in height / 10 until height * 9 / 10 step step) for (x in 0 until ALIGNMENT_COLUMNS) {
            samples++
            val index = y * ALIGNMENT_COLUMNS + x
            if (firstChanges?.getOrNull(index) == true || secondChanges?.getOrNull(index) == true) {
                excluded++
                continue
            }
            if (colorDifference(first[index], second[index]) > 6) return false
            retained++
            val neighbor = if (x + 1 < ALIGNMENT_COLUMNS) index + 1 else index - 1
            if (maxOf(colorDifference(first[index], first[neighbor]),
                    colorDifference(second[index], second[neighbor])) >= MIN_FEATURE_CONTRAST) {
                features++
                featureBands[minOf(15, y * 16 / height)] = true
                if (y in height / 4 until height * 3 / 4) middleFeatures++
            }
        }
        return excluded > 0 && retained >= samples * 0.35f && features >= 24 && middleFeatures >= 8 && featureBands.count { it } >= 3
    }

    private fun sampledColumns(frame: PixelSlice): IntArray {
        val startX = (frame.width * 0.08f).toInt()
        val endX = (frame.width * 0.92f).toInt()
        return IntArray(frame.height * ALIGNMENT_COLUMNS) { index ->
            val x = startX + (endX - startX - 1) * (index % ALIGNMENT_COLUMNS) / (ALIGNMENT_COLUMNS - 1)
            frame.getPixel(x, index / ALIGNMENT_COLUMNS)
        }
    }

    private fun fixedEdge(first: IntArray, second: IntArray, height: Int, fromBottom: Boolean): Int {
        var differentRows = 0
        var lastSame = 0
        // A short noisy clock or edge antialiasing must not terminate a fixed toolbar.
        // Stop on a sustained differing run; never remove the central content area.
        val run = maxOf(3, height / 200)
        for (offset in 0 until height * 2 / 5) {
            val row = if (fromBottom) height - offset - 1 else offset
            var error = 0
            for (x in 0 until ALIGNMENT_COLUMNS) {
                error += colorDifference(first[row * ALIGNMENT_COLUMNS + x], second[row * ALIGNMENT_COLUMNS + x])
            }
            if (error.toFloat() / ALIGNMENT_COLUMNS <= SAME_POSITION_DIFFERENCE) {
                lastSame = offset + 1
                differentRows = 0
            } else if (++differentRows >= run) return lastSame
        }
        return lastSame
    }

    private fun alignmentDifference(first: IntArray, second: IntArray, top: Int, bottom: Int, displacement: Int): Float {
        val startY = displacement + top
        val stepY = maxOf(1, (bottom - top) / 256)
        val rows = (bottom - startY + stepY - 1) / stepY
        val total = rows * ALIGNMENT_COLUMNS
        var rawAgreeing = 0
        var temporalChanges = 0
        dynamicPixels.fill(false, 0, total)
        for (row in 0 until rows) {
            val a = (startY + row * stepY) * ALIGNMENT_COLUMNS
            val b = a - displacement * ALIGNMENT_COLUMNS
            for (x in 0 until ALIGNMENT_COLUMNS) {
                val error = colorDifference(first[a + x], second[b + x])
                val index = row * ALIGNMENT_COLUMNS + x
                val changing = earlierChanges?.getOrNull(a + x) == true || laterChanges?.getOrNull(b + x) == true
                if (changing) temporalChanges++
                dynamicPixels[index] = changing
                residuals[index] = if (changing) 0 else error
                if (changing || error <= PIXEL_AGREEMENT) rawAgreeing++
            }
        }
        if (total == 0 || rawAgreeing < total * 0.65f) return Float.POSITIVE_INFINITY

        // Animation creates a large connected patch of residuals. A wrong repeated
        // paragraph creates small distinctive heading residuals. Remove only sustained
        // patches spanning several columns, keeping those headings for seam uniqueness.
        val minimumRun = maxOf(4, (bottom - top) / 20 / stepY)
        for (x in 0 until ALIGNMENT_COLUMNS) {
            var run = 0
            for (row in 0 until rows) {
                val index = row * ALIGNMENT_COLUMNS + x
                run = if (residuals[index] > PIXEL_AGREEMENT) run + 1 else 0
                changedRuns[index] = run
            }
        }
        for (x in 0 until ALIGNMENT_COLUMNS) {
            for (row in 0 until rows) {
                val index = row * ALIGNMENT_COLUMNS + x
                val run = changedRuns[index]
                if (run < minimumRun || (row + 1 < rows && changedRuns[index + ALIGNMENT_COLUMNS] > 0)) continue
                var neighbors = 0
                for (neighbor in maxOf(0, x - 2)..minOf(ALIGNMENT_COLUMNS - 1, x + 2)) {
                    if (changedRuns[row * ALIGNMENT_COLUMNS + neighbor] >= minimumRun) neighbors++
                }
                if (neighbors < 3) continue
                for (affected in row - run + 1..row) dynamicPixels[affected * ALIGNMENT_COLUMNS + x] = true
            }
        }
        var sum = 0L
        var count = 0
        var agreeing = 0
        var informative = 0
        var informativeAgreeing = 0
        for (row in 0 until rows) {
            val y = startY + row * stepY
            val a = y * ALIGNMENT_COLUMNS
            val b = (y - displacement) * ALIGNMENT_COLUMNS
            for (x in 0 until ALIGNMENT_COLUMNS) {
                val index = row * ALIGNMENT_COLUMNS + x
                if (dynamicPixels[index]) continue
                val error = residuals[index]
                // Cap each residual, retaining distinctive headings as evidence while a
                // local video or loading thumbnail cannot dominate the entire viewport.
                sum += minOf(error, MAX_PIXEL_RESIDUAL)
                if (error <= PIXEL_AGREEMENT) agreeing++
                val neighbor = if (x + 1 < ALIGNMENT_COLUMNS) x + 1 else x - 1
                if (maxOf(colorDifference(first[a + x], first[a + neighbor]),
                        colorDifference(second[b + x], second[b + neighbor])) >= MIN_FEATURE_CONTRAST) {
                    informative++
                    if (error <= PIXEL_AGREEMENT) informativeAgreeing++
                }
                count++
            }
        }
        // Matching blank backgrounds is insufficient. Most visible features must agree,
        // and competing shifts still go through the global ambiguity test above.
        // A known playing video may occupy most of a short overlap. Require an absolute
        // viewport-sized amount of stable evidence, rather than a fraction of that video.
        // Unexplained residual patches still cannot erase most of the remaining evidence.
        val minimumEvidenceRows = (maxOf(24, first.size / ALIGNMENT_COLUMNS / 10) + stepY - 1) / stepY
        if (count < minimumEvidenceRows * ALIGNMENT_COLUMNS || count < (total - temporalChanges) * 0.65f ||
            agreeing < count * 0.65f || informative < 12 ||
            informativeAgreeing < informative * 0.60f) return Float.POSITIVE_INFINITY
        return sum.toFloat() / count
    }

    private fun searchRange(frame: PixelSlice): Int = maxOf(matcher.maxSearchRange, (frame.height * 0.65f).toInt())

    private fun sameGeometry(first: PixelSlice, second: PixelSlice): Boolean =
        first.width == second.width && first.height == second.height && first.height > 0

    /**
     * Sample actual pixels as well as the matcher's row averages. Exclude fixed edge chrome
     * when checking a displacement; trim a few noisy rows for clocks, controls and animations.
     */
    private fun difference(first: PixelSlice, second: PixelSlice, displacement: Int = 0): Float {
        if (!sameGeometry(first, second)) return Float.POSITIVE_INFINITY
        val topMargin = if (displacement > 0) (first.height * 0.10f).toInt() else 0
        val bottomMargin = if (displacement > 0) (first.height * 0.12f).toInt() else 0
        val startY = displacement + topMargin
        val endY = first.height - bottomMargin
        if (endY - startY < 12) return Float.POSITIVE_INFINITY
        val stepY = maxOf(1, (endY - startY) / 96)
        val startX = (first.width * 0.08f).toInt()
        val endX = (first.width * 0.92f).toInt()
        val stepX = maxOf(1, (endX - startX) / 32)
        val rowErrors = ArrayList<Float>(97)
        for (y in startY until endY step stepY) {
            var sum = 0L
            var count = 0
            for (x in startX until endX step stepX) {
                sum += colorDifference(first.getPixel(x, y), second.getPixel(x, y - displacement))
                count++
            }
            rowErrors.add(sum.toFloat() / maxOf(1, count))
        }
        rowErrors.sort()
        val keep = maxOf(1, (rowErrors.size * 0.90f).toInt())
        var sum = 0f
        for (index in 0 until keep) sum += rowErrors[index]
        return sum / keep
    }

    private fun colorDifference(first: Int, second: Int): Int =
        (abs(((first ushr 16) and 255) - ((second ushr 16) and 255)) +
            abs(((first ushr 8) and 255) - ((second ushr 8) and 255)) +
            abs((first and 255) - (second and 255))) / 3

    companion object {
        private const val SAME_POSITION_DIFFERENCE = 2.5f
        private const val MAX_ALIGNMENT_DIFFERENCE = 10f
        private const val MAX_PIXEL_RESIDUAL = 32
        private const val PIXEL_AGREEMENT = 6
        private const val MIN_FEATURE_CONTRAST = 8
        private const val ALIGNMENT_COLUMNS = 32
    }
}
