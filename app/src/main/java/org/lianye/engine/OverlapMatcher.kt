package org.lianye.engine

import org.lianye.engine.model.LumaSignature
import org.lianye.engine.model.MatchResult
import org.lianye.engine.model.PixelSlice
import java.util.Arrays

class OverlapMatcher(
    val templateHeight: Int = 256,
    val maxSearchRange: Int = 800,
    val trimRatio: Float = 0.15f,
    val screenDensity: Float = 2.0f
) {
    init {
        require(templateHeight in 32..1024) { "templateHeight $templateHeight must be within 32..1024" }
        require(trimRatio in 0.0f..<1.0f) { "trimRatio $trimRatio must be in [0.0, 1.0)" }
        require(maxSearchRange > 0) { "maxSearchRange must be positive" }
    }

    // Pre-allocated scratch buffers to guarantee Zero Heap Allocation in search loop
    private val scratchResiduals = IntArray(1024)
    private val scratchCounts = IntArray(1024)

    fun extractLumaSignature(slice: PixelSlice): LumaSignature {
        val w = slice.width
        val h = slice.height
        require(w >= 32) { "Width $w is too small for 3-column analysis" }

        val startX = (w * 0.05f).toInt()
        val endX = (w * 0.90f).toInt()
        val effW = maxOf(1, endX - startX)

        val col1End = startX + (effW * 0.25f).toInt()
        val col2End = startX + (effW * 0.75f).toInt()

        val leftLuma = IntArray(h)
        val centerLuma = IntArray(h)
        val rightLuma = IntArray(h)
        val horizontalVar = LongArray(0)

        val leftCount = maxOf(1, col1End - startX)
        val centerCount = maxOf(1, col2End - col1End)
        val rightCount = maxOf(1, endX - col2End)

        for (y in 0 until h) {
            var sumLeft = 0L
            var sumCenter = 0L
            var sumRight = 0L

            for (x in startX until endX) {
                val p = slice.getPixel(x, y)
                // P1 Fix: ushr for unsigned byte unpacking
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val luma = (77 * r + 150 * g + 29 * b) ushr 8

                when {
                    x < col1End -> sumLeft += luma
                    x < col2End -> sumCenter += luma
                    else -> sumRight += luma
                }
            }

            leftLuma[y] = (sumLeft / leftCount).toInt()
            centerLuma[y] = (sumCenter / centerCount).toInt()
            rightLuma[y] = (sumRight / rightCount).toInt()
        }

        return LumaSignature(h, leftLuma, centerLuma, rightLuma, horizontalVar)
    }

    fun chooseTemplateStart(
        prevSlice: PixelSlice,
        sig: LumaSignature,
        rowMask: BooleanArray? = null,
        density: Float = screenDensity,
        minStartY: Int = 0
    ): Int {
        val h = sig.height
        val tH = minOf(templateHeight, h)
        if (h <= tH) return 0

        val topMarginPx = minOf((h * 0.25f).toInt(), maxOf((h * 0.10f).toInt(), (80 * density).toInt()))
        val bottomMarginPx = minOf((h * 0.28f).toInt(), maxOf((h * 0.14f).toInt(), (100 * density).toInt()))

        val scanStart = minOf(h - tH, (h - bottomMarginPx) - tH)
        // The template must start low enough that the expected displacement is searchable:
        // match() bounds the shift by tStartPrev, so a template placed above the real
        // scroll distance silently makes the true offset unreachable (field bug: 653px
        // strokes answered with 44/116px noise peaks).
        val scanEnd = maxOf(0, topMarginPx, minStartY)

        if (scanStart <= scanEnd) {
            return minOf(maxOf(0, (h - tH) / 2, minStartY), maxOf(0, h - tH))
        }

        var bestY = scanStart
        var maxNormalizedVariance = -1.0f

        for (y in scanStart downTo scanEnd) {
            var sumL = 0L
            var sumSq = 0L
            var gradEnergy = 0L
            var staticCount = 0

            for (j in 0 until tH) {
                val rowY = y + j
                if (rowMask != null && rowY < rowMask.size && !rowMask[rowY]) {
                    staticCount++
                }
                // Score texture across ALL three columns. WeChat-style chat rows keep the
                // bubbles in the right column and leave the center blank; center-only
                // scoring then sees a flat field and picks a featureless template whose SAD
                // landscape is noise (field log: sad=0.12 at a bogus deltaY=44).
                val l = ((sig.leftLuma[rowY] + sig.centerLuma[rowY] + sig.rightLuma[rowY]) / 3).toLong()
                sumL += l
                sumSq += (l * l)
                if (j > 0) {
                    val prevRowY = rowY - 1
                    val prevL = ((sig.leftLuma[prevRowY] + sig.centerLuma[prevRowY] + sig.rightLuma[prevRowY]) / 3).toLong()
                    gradEnergy += kotlin.math.abs(l - prevL)
                }
            }

            if (staticCount > tH / 4) continue // Avoid selecting static headers as template

            val meanLuma = (sumL.toDouble() / tH).toFloat()
            val rawVariance = (sumSq.toDouble() / tH - meanLuma * meanLuma).toFloat()
            val contrastFactor = maxOf(5.0f, (meanLuma * (255.0f - meanLuma)) / 255.0f)
            val normalizedVar = rawVariance / contrastFactor
            val minGradThreshold = maxOf(20L, (80L * (contrastFactor / 63.75f)).toLong())

            if (normalizedVar > maxNormalizedVariance) {
                maxNormalizedVariance = normalizedVar
                bestY = y
            }

            if (normalizedVar >= 0.5f && gradEnergy >= minGradThreshold) {
                return y
            }
        }

        return bestY
    }

    fun match(
        prevSlice: PixelSlice,
        nextSlice: PixelSlice,
        rowMask: BooleanArray? = null,
        priorDeltaY: Int? = null,
        searchRangeOverride: Int? = null,
        expectedDeltaY: Int? = null
    ): MatchResult {
        require(searchRangeOverride == null || searchRangeOverride > 0) { "Search range must be positive" }
        // 1. Check for FLAG_SECURE all-black detection
        if (isFrameAllBlack(nextSlice)) {
            return MatchResult(
                deltaY = 0,
                sadScore = 0f,
                ambiguityRatio = 1.0f,
                isBottomReached = false,
                isSecureBlocked = true
            )
        }

        val sigPrev = extractLumaSignature(prevSlice)
        val sigNext = extractLumaSignature(nextSlice)

        val tH = minOf(templateHeight, sigPrev.height)
        // The real scroll length of the injected stroke is known (finger travel). Anchor the
        // template low enough in the frame that this displacement is searchable at all:
        // match() can only shift the template down by tStartPrev rows, so a template placed
        // above the true offset makes the correct peak unreachable and the search collapses
        // onto noise (field report: 653px strokes answered 116px / 44px → dropped rows).
        val minTemplateStart = if (expectedDeltaY != null && expectedDeltaY > 0) {
            minOf(expectedDeltaY, maxSearchRange, maxOf(0, sigPrev.height - tH))
        } else {
            0
        }
        val tStartPrev = chooseTemplateStart(prevSlice, sigPrev, rowMask, screenDensity, minStartY = minTemplateStart)

        val stationaryLimitPx = maxOf(4, (6.0f * screenDensity).toInt())
        val peakSuppressionRadius = maxOf(6, (8.0f * screenDensity).toInt())

        val maxShift = minOf(searchRangeOverride ?: maxSearchRange, tStartPrev)
        var minScore1 = Float.MAX_VALUE
        var bestDeltaY1 = 0

        var minScore2 = Float.MAX_VALUE
        var bestDeltaY2 = 0

        val scores = FloatArray(maxShift + 1) { Float.MAX_VALUE }

        val searchStep = 1
        for (deltaY in 0..maxShift step searchStep) {
            val tStartNext = tStartPrev - deltaY
            if (tStartNext < 0 || tStartNext + tH > sigNext.height) continue

            val score = computeTrimmedSad(
                sigPrev, tStartPrev,
                sigNext, tStartNext,
                tH, rowMask
            )
            scores[deltaY] = score

            if (score < minScore1) {
                if (kotlin.math.abs(deltaY - bestDeltaY1) >= peakSuppressionRadius) {
                    minScore2 = minScore1
                    bestDeltaY2 = bestDeltaY1
                }
                minScore1 = score
                bestDeltaY1 = deltaY
            } else if (score < minScore2 && kotlin.math.abs(deltaY - bestDeltaY1) >= peakSuppressionRadius) {
                minScore2 = score
                bestDeltaY2 = deltaY
            }
        }

        // Laplace smoothed ambiguity ratio: ratio >= 0.85 means best score is within 15% of second best
        val ambiguityRatio = if (minScore2 < Float.MAX_VALUE) {
            (minScore1 + 1.0f) / (minScore2 + 1.0f)
        } else {
            0.0f
        }

        // --- 1. Find best stationary candidate in 0..stationaryLimitPx window ---
        var minStationaryScore = Float.MAX_VALUE
        var bestStationaryDeltaY = 0
        val stationaryLimit = minOf(stationaryLimitPx, maxShift)
        for (d in 0..stationaryLimit) {
            if (scores[d] < minStationaryScore) {
                minStationaryScore = scores[d]
                bestStationaryDeltaY = d
            }
        }

        var finalDeltaY = bestDeltaY1
        var finalScore = minScore1

        // --- 2. Disambiguation with Prior Displacement ---
        if (ambiguityRatio >= 0.85f && priorDeltaY != null && priorDeltaY > stationaryLimitPx) {
            var minPenalizedSlidingScore = Float.MAX_VALUE
            var bestSlidingDeltaY = -1
            var bestSlidingScore = Float.MAX_VALUE

            val slidingThreshold = minScore1 * 1.15f + 2.0f
            val sigma = maxOf(8.0f, 0.25f * priorDeltaY)
            val twoSigmaSq = 2.0f * sigma * sigma

            for (deltaY in (stationaryLimitPx + 1)..maxShift step searchStep) {
                val s = scores[deltaY]
                if (s <= slidingThreshold) {
                    val diff = (deltaY - priorDeltaY).toFloat()
                    val penalty = (diff * diff) / twoSigmaSq
                    val penalized = s + penalty
                    if (penalized < minPenalizedSlidingScore) {
                        minPenalizedSlidingScore = penalized
                        bestSlidingDeltaY = deltaY
                        bestSlidingScore = s
                    }
                }
            }

            var sumL = 0L
            var sumSq = 0L
            for (j in 0 until tH) {
                val l = sigPrev.centerLuma[tStartPrev + j].toLong()
                sumL += l
                sumSq += l * l
            }
            val mean = sumL.toDouble() / tH
            val rawVar = (sumSq.toDouble() / tH - mean * mean).toFloat()
            val contrastFactor = maxOf(5.0f, (mean.toFloat() * (255.0f - mean.toFloat())) / 255.0f)
            val normalizedVar = rawVar / contrastFactor

            if (bestSlidingDeltaY > stationaryLimitPx) {
                // If the global minimum was already stationary (bestDeltaY1 <= stationaryLimitPx),
                // only allow the sliding candidate to override stationary if the template actually
                // has significant feature texture (normalizedVar >= 0.5f) and is a true periodic tie
                // (sliding score within 1.5 of stationary score).
                // Flat/low-texture backgrounds must NEVER be mistaken for periodic patterns.
                val isTruePeriodicTie = (normalizedVar >= 0.5f) && (bestSlidingScore <= minStationaryScore + 1.5f)
                if (bestDeltaY1 > stationaryLimitPx || isTruePeriodicTie) {
                    finalDeltaY = bestSlidingDeltaY
                    finalScore = bestSlidingScore
                } else {
                    finalDeltaY = bestStationaryDeltaY
                    finalScore = minStationaryScore
                }
            } else if (bestDeltaY1 <= stationaryLimitPx) {
                finalDeltaY = bestStationaryDeltaY
                finalScore = minStationaryScore
            }
        } else if (bestDeltaY1 <= stationaryLimitPx) {
            finalDeltaY = bestStationaryDeltaY
            finalScore = minStationaryScore
        }

        // --- 2b. Expected-stroke tie-break (known stroke length) ---
        // The injected stroke travels a known distance (finger travel ≈ actual page scroll
        // for a controlled drag). Near-periodic list rows (chat bubbles at a fixed pitch)
        // create alias peaks at trueShift ± k*pitch that score almost identically once the
        // 3-column luma average washes out row details, and a flat template makes every
        // offset look equally good (field: true 653px stroke answered 116px and 44px with
        // sad≈0.1 — and both values sat exactly at the search boundary — silently dropping
        // 500-1100px of content per seam). When the landscape is ambiguous or the pick is
        // untrustworthy, re-anchor to the near-tie candidate closest to the commanded
        // stroke. A genuine early stop still wins: identical frames (global 0-shift diff
        // ≈ 0) are never re-anchored, and a clean unambiguous match keeps its delta (this
        // preserves real partial scrolls at the tail of the page).
        if (expectedDeltaY != null && expectedDeltaY > stationaryLimitPx && maxShift > stationaryLimitPx) {
            val cmpH = minOf(sigPrev.height, sigNext.height)
            var gSum = 0L
            for (y in 0 until cmpH) {
                gSum += kotlin.math.abs(sigPrev.leftLuma[y] - sigNext.leftLuma[y])
                gSum += 2L * kotlin.math.abs(sigPrev.centerLuma[y] - sigNext.centerLuma[y])
                gSum += kotlin.math.abs(sigPrev.rightLuma[y] - sigNext.rightLuma[y])
            }
            val globalZeroShiftDiff = gSum.toFloat() / (4.0f * cmpH)

            val ambiguousOrUntrustworthy = ambiguityRatio >= 0.7f || finalScore > 6.0f
            if (globalZeroShiftDiff >= 4.0f && ambiguousOrUntrustworthy && minScore1 < Float.MAX_VALUE) {
                val tieTol = maxOf(0.5f, minScore1 * 0.2f)
                var bestDist = Int.MAX_VALUE
                var tiedDeltaY = -1
                var tiedScore = Float.MAX_VALUE
                for (d in 0..maxShift) {
                    val s = scores[d]
                    if (s <= minScore1 + tieTol && s <= maxOf(finalScore * 1.2f, finalScore + 1.0f)) {
                        val dist = kotlin.math.abs(d - expectedDeltaY)
                        if (dist < bestDist) {
                            bestDist = dist
                            tiedDeltaY = d
                            tiedScore = s
                        }
                    }
                }
                if (tiedDeltaY > stationaryLimitPx && kotlin.math.abs(finalDeltaY - expectedDeltaY) > bestDist) {
                    finalDeltaY = tiedDeltaY
                    finalScore = tiedScore
                }
            }
        }

        // --- 3. Stationary, Dynamic Scene & Bottom-reached Detection ---
        val isStationary = finalDeltaY <= stationaryLimitPx
        // A truly "bottom reached" frame is one where the page stopped moving AND the residual
        // is essentially zero (real stops measure ~0.0-1.5 SAD after column averaging; anything
        // meaningfully higher is a deformed overlap, not a stop). The old `<= 3.5f` cutoff was
        // so loose that a real capture-timing-deformed overlap (field-measured sad=13.40 on a
        // Pixel 9a) tripped it on the very first frame and aborted the capture at 2 frames.
        // Tightening to 4.0 + requiring two consecutive stationary frames (engine side) makes
        // the stop decision robust against single-frame noise without hanging on a real page.
        val isBottomReached = isStationary && (finalScore <= 4.0f)
        val isDynamicScene = !isStationary && (finalScore / 4.0f > 10.0f)
        // A stationary alignment whose residual is far above clean-stop levels is a deformed
        // capture (the 0-shift peak is not a true match). Don't call it a stop — the engine
        // retries the frame; if the deformation persists the `newStatCount >= 2` path will
        // terminate safely without corrupting the stitched image.
        val isDeformedMatch = isStationary && !isBottomReached && finalScore > 8.0f
        val cleanDeltaY = if (isStationary) 0 else finalDeltaY

        val result = MatchResult(
            deltaY = cleanDeltaY,
            sadScore = finalScore,
            ambiguityRatio = ambiguityRatio,
            isBottomReached = isBottomReached,
            isSecureBlocked = false,
            isStationary = isStationary,
            isDynamicScene = isDynamicScene,
            isDeformedMatch = isDeformedMatch
        )
        org.lianye.platform.LianyeLogStore.frameMatch(
            frameIndex = -1,
            deltaY = result.deltaY,
            isBottomReached = result.isBottomReached,
            isStationary = result.isStationary,
            isDynamic = result.isDynamicScene,
            sadScore = result.sadScore,
            ambiguityRatio = result.ambiguityRatio,
            isDeformed = result.isDeformedMatch
        )
        return result
    }

    private fun computeTrimmedSad(
        sigPrev: LumaSignature, startPrev: Int,
        sigNext: LumaSignature, startNext: Int,
        tH: Int, rowMask: BooleanArray?
    ): Float {
        var validCount = 0

        for (j in 0 until tH) {
            val yPrev = startPrev + j
            val yNext = startNext + j

            if (rowMask != null) {
                val isNextStatic = yNext < rowMask.size && !rowMask[yNext]
                val isPrevStatic = yPrev < rowMask.size && !rowMask[yPrev]
                if (isNextStatic || isPrevStatic) {
                    continue // Skip static rows (e.g. AppBar / TabBar)
                }
            }

            val diffLeft = kotlin.math.abs(sigPrev.leftLuma[yPrev] - sigNext.leftLuma[yNext])
            val diffCenter = kotlin.math.abs(sigPrev.centerLuma[yPrev] - sigNext.centerLuma[yNext])
            val diffRight = kotlin.math.abs(sigPrev.rightLuma[yPrev] - sigNext.rightLuma[yNext])

            val diffTotal = minOf(1023, diffLeft + 2 * diffCenter + diffRight)
            scratchResiduals[validCount++] = diffTotal
        }

        if (validCount == 0) return Float.MAX_VALUE

        val keepCount = maxOf(1, (validCount * (1.0f - trimRatio)).toInt())

        // P1 Fix: Zero-allocation counting sort selection
        Arrays.fill(scratchCounts, 0)
        for (i in 0 until validCount) {
            scratchCounts[scratchResiduals[i]]++
        }

        var accumulated = 0
        var cutoff = 1023
        for (v in 0..1023) {
            accumulated += scratchCounts[v]
            if (accumulated >= keepCount) {
                cutoff = v
                break
            }
        }

        var sumInliers = 0L
        var countInliers = 0
        for (i in 0 until validCount) {
            val r = scratchResiduals[i]
            if (r < cutoff && countInliers < keepCount) {
                sumInliers += r
                countInliers++
            }
        }
        while (countInliers < keepCount) {
            sumInliers += cutoff
            countInliers++
        }

        return sumInliers.toFloat() / keepCount
    }

    internal fun isFrameAllBlack(slice: PixelSlice): Boolean {
        val stepX = maxOf(1, slice.width / 30)
        val stepY = maxOf(1, slice.height / 40)
        var total = 0
        var black = 0
        for (y in 0 until slice.height step stepY) {
            for (x in 0 until slice.width step stepX) {
                total++
                val pixel = slice.getPixel(x, y)
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                if (r <= 2 && g <= 2 && b <= 2) {
                    black++
                }
            }
        }
        return total > 0 && black == total
    }
}
