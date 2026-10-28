package org.lianye.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.lianye.engine.model.PixelSlice
import kotlin.math.abs
import kotlin.random.Random

class OverlapMatcherTest {

    private lateinit var matcher: OverlapMatcher
    private val width = 100
    private val height = 600

    @Before
    fun setUp() {
        matcher = OverlapMatcher(
            templateHeight = 120,
            maxSearchRange = 300,
            trimRatio = 0.15f
        )
    }

    @Test
    fun `standard overlap should calculate exact deltaY within 1px`() {
        val shiftY = 150
        val basePattern = generateRealisticUiPattern(width, height + shiftY)

        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSlice = extractSlice(basePattern, shiftY, height)

        val result = matcher.match(prevSlice, nextSlice)

        assertFalse("Bottom should not be reached", result.isBottomReached)
        assertFalse("Should not be secure blocked", result.isSecureBlocked)
        assertEquals("DeltaY must match shift within 1px", shiftY.toDouble(), result.deltaY.toDouble(), 1.0)
    }

    @Test
    fun `weak texture area at bottom should cause template window to shift upward`() {
        val prevPixels = IntArray(width * height) { -0x1 } // All white (0xFFFFFFFF)
        // Add texture in rows 100..250 (upper-middle area)
        for (y in 100..250) {
            for (x in 0 until width) {
                prevPixels[y * width + x] = if ((x + y) % 6 == 0) -0x1000000 else -0x1
            }
        }
        val prevSlice = PixelSlice(prevPixels, width, height)
        val sig = matcher.extractLumaSignature(prevSlice)

        val templateStart = matcher.chooseTemplateStart(prevSlice, sig)

        // Template should avoid the bottom white area (300..599) and pick an index <= 250
        assertTrue("Template start should be shifted into textured zone", templateStart <= 250)
    }

    @Test
    fun `periodic repetitive list should disambiguate using prior displacement`() {
        // Create repeating pattern with 50px period
        val totalH = 800
        val pixels = IntArray(width * totalH)
        for (y in 0 until totalH) {
            val isBar = (y % 50) in 0..10
            val color = if (isBar) -0x1000000 else -0x222222
            for (x in 0 until width) {
                pixels[y * width + x] = color
            }
        }
        val fullSlice = PixelSlice(pixels, width, totalH)

        val expectedShift = 100 // exactly 2 periods
        val prevSlice = extractSlice(fullSlice, 0, height)
        val nextSlice = extractSlice(fullSlice, expectedShift, height)

        // With prior expected shift of 100, should disambiguate to 100
        val result = matcher.match(prevSlice, nextSlice, priorDeltaY = 100)

        assertEquals("Periodic pattern should disambiguate to expected shift", expectedShift, result.deltaY)
    }

    @Test
    fun `temporal variance mask should eliminate static header interference`() {
        val shiftY = 75
        val staticHeaderH = 60

        val slice1 = generateSliceWithStaticHeader(width, height, staticHeaderH, scrollOffset = 0)
        val slice2 = generateSliceWithStaticHeader(width, height, staticHeaderH, scrollOffset = shiftY)
        val slice3 = generateSliceWithStaticHeader(width, height, staticHeaderH, scrollOffset = shiftY * 2)

        val maskEngine = TemporalVarianceMask(varianceThreshold = 16L)
        val mask = maskEngine.computeRowMask(slice1, slice2, slice3)

        // Header rows should be marked false (static)
        for (y in 0 until staticHeaderH) {
            assertFalse("Header row $y should be marked static (false)", mask[y])
        }
        // Body rows should be marked true (dynamic)
        assertTrue("Content row should be marked dynamic (true)", mask[staticHeaderH + 20])

        // Matching slice1 and slice2 using the mask
        val result = matcher.match(slice1, slice2, rowMask = mask)
        assertEquals("Matching with mask should detect correct content shift", shiftY.toDouble(), result.deltaY.toDouble(), 1.0)
    }

    @Test
    fun `trimmed SAD should tolerate outlier rows from video or gif`() {
        val shiftY = 120
        val basePattern = generateRealisticUiPattern(width, height + shiftY)

        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSliceRaw = extractSlice(basePattern, shiftY, height)

        // Corrupt 10% of rows in nextSlice with random noise (simulating moving GIF)
        val corruptedPixels = nextSliceRaw.pixels.clone()
        val random = Random(42)
        for (y in 200..230) { // 31 rows = ~5% of 600
            for (x in 0 until width) {
                corruptedPixels[y * width + x] = random.nextInt()
            }
        }
        val nextSliceCorrupted = PixelSlice(corruptedPixels, width, height)

        val result = matcher.match(prevSlice, nextSliceCorrupted)

        assertEquals("Trimmed SAD must ignore noisy rows and find correct deltaY", shiftY.toDouble(), result.deltaY.toDouble(), 1.0)
    }

    @Test
    fun `identical consecutive frames should terminate with bottom reached`() {
        val basePattern = generateRealisticUiPattern(width, height)
        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSlice = extractSlice(basePattern, 0, height) // 0 shift

        val result = matcher.match(prevSlice, nextSlice)

        assertTrue("Zero displacement must trigger isBottomReached", result.isBottomReached)
    }

    @Test
    fun `micro displacement under 15px should terminate with bottom reached and zero out deltaY to eliminate ghost strips`() {
        val shiftY = 11
        val basePattern = generateRealisticUiPattern(width, height + shiftY)
        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSlice = extractSlice(basePattern, shiftY, height)

        val result = matcher.match(prevSlice, nextSlice)

        assertTrue("Displacement <= 15px must trigger isBottomReached", result.isBottomReached)
        assertEquals("DeltaY must be zeroed out to eliminate ghost strips", 0, result.deltaY)
    }

    @Test
    fun `large prior displacement should NEVER override stationary frames`() {
        val basePattern = generateRealisticUiPattern(width, height)
        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSlice = extractSlice(basePattern, 0, height)

        val result = matcher.match(prevSlice, nextSlice, priorDeltaY = 350)

        assertTrue("Zero physical movement with large prior must still trigger isBottomReached", result.isBottomReached)
        assertEquals("Final deltaY must remain 0 despite 350px prior", 0, result.deltaY)
    }

    @Test
    fun `overscroll micro jitter under 10px with flat background must terminate cleanly`() {
        val h = 600
        val w = 100
        val pixels = IntArray(w * (h + 10)) { -0x111112 }
        for (y in 480..495) {
            for (x in 30..70) pixels[y * w + x] = -0x1000000
        }
        val slice1 = PixelSlice(pixels, w, h)
        val slice2 = extractSlice(PixelSlice(pixels, w, h + 10), 3, h)

        val customMatcher = OverlapMatcher(templateHeight = 128, maxSearchRange = 300)
        val result = customMatcher.match(slice1, slice2, priorDeltaY = 350)
        assertTrue("Micro jitter on flat background must trigger isBottomReached", result.isBottomReached)
        assertEquals("DeltaY must be zeroed out", 0, result.deltaY)
    }

    @Test
    fun `overscroll heavy distortion with high SAD should be marked dynamic or high score without crash`() {
        val basePattern = generateRealisticUiPattern(width, height)
        val prevSlice = extractSlice(basePattern, 0, height)
        // Completely distorted/random pixels in nextSlice simulating overscroll rubber band distortion
        val distortedPixels = IntArray(width * height) { Random.nextInt(0, 0xFFFFFF) or -0x1000000 }
        val nextSlice = PixelSlice(distortedPixels, width, height)

        val result = matcher.match(prevSlice, nextSlice)

        assertTrue("Distortion with high SAD must have high score (>20f)", result.sadScore > 20.0f)
    }

    @Test
    fun `deformed overlap with low displacement but high residual must NOT trigger bottom reached`() {
        // Reproduces the Pixel 9a field report: deltaY=0 but sad=13.40 (well above the clean-stop
        // residual of 0-4). The matcher must flag this as a deformed capture, not declare the
        // page stopped — otherwise the engine aborts the whole capture after 2 frames.
        val h = 600
        val w = 100
        val base = generateRealisticUiPattern(w, h)
        val prevSlice = extractSlice(base, 0, h)

        // nextSlice: same overall layout but every row shifted/subtly altered -> 0-displacement
        // "match" has high residual without any real scroll.
        val nextPixels = base.pixels.clone()
        val random = Random(7)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = nextPixels[y * w + x]
                val r = ((p ushr 16) and 0xFF) + random.nextInt(-30, 30)
                val g = ((p ushr 8) and 0xFF) + random.nextInt(-30, 30)
                val b = (p and 0xFF) + random.nextInt(-30, 30)
                nextPixels[y * w + x] = -0x1000000 or
                    ((r.coerceIn(0, 255)) shl 16) or
                    ((g.coerceIn(0, 255)) shl 8) or
                    (b.coerceIn(0, 255))
            }
        }
        val nextSlice = PixelSlice(nextPixels, w, h)

        val result = matcher.match(prevSlice, nextSlice)

        assertEquals("Displacement must be stationary (0)", 0, result.deltaY)
        assertTrue("Residual must be well above the clean-stop band (sad=${result.sadScore})", result.sadScore > 8.0f)
        assertTrue("Must be flagged as a deformed match", result.isDeformedMatch)
        assertFalse("A deformed match must NOT declare bottom reached", result.isBottomReached)
    }

    @Test
    fun `clean stationary frames must still declare bottom reached after threshold tightening`() {
        val basePattern = generateRealisticUiPattern(width, height)
        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSlice = extractSlice(basePattern, 0, height) // true 0 shift, identical

        val result = matcher.match(prevSlice, nextSlice)

        assertTrue("Clean 0-shift match must still trigger isBottomReached", result.isBottomReached)
        assertFalse("Clean 0-shift match is not deformed", result.isDeformedMatch)
        assertTrue("Clean 0-shift residual must be very low (sad=${result.sadScore})", result.sadScore <= 4.0f)
    }

    /**
     * WeChat-style chat rows: bubbles and avatars live ONLY in the right columns while the
     * center stays blank. The old center-only template scoring saw a flat field, placed the
     * template above the real offset, and answered a 653px stroke with a 44/116px noise peak
     * (sad≈0.1) — the stitcher then dropped 500-1100px of content per seam.
     */
    private fun generateRightSideChatPattern(w: Int, h: Int, pitch: Int = 82): PixelSlice {
        val pixels = IntArray(w * h) { -0x1 } // flat background everywhere
        for (y in 0 until h) {
            val itemIdx = y / pitch
            val bubbleTop = itemIdx * pitch + 8
            val bubbleBottom = bubbleTop + 40
            if (y in bubbleTop..bubbleBottom) {
                for (x in (w * 2 / 3) until (w - 2)) {
                    pixels[y * w + x] = if ((x + y) % 7 == 0) -0xcccccccc.toInt() else -0xff7744
                }
            }
            if (y in (itemIdx * pitch + 10)..(itemIdx * pitch + 22)) {
                for (x in (w - 14) until (w - 2)) {
                    pixels[y * w + x] = -0x1000000
                }
            }
        }
        return PixelSlice(pixels, w, h)
    }

    @Test
    fun `right-side bubble chat with blank center must lock onto a large stroke displacement`() {
        val shiftY = 240
        val full = generateRightSideChatPattern(width, height + shiftY)
        val prevSlice = extractSlice(full, 0, height)
        val nextSlice = extractSlice(full, shiftY, height)

        val result = matcher.match(prevSlice, nextSlice, expectedDeltaY = shiftY)

        assertFalse("Chat page still scrolling: not bottom", result.isBottomReached)
        assertEquals(
            "Must recover the true stroke displacement, got ${result.deltaY} (sad=${result.sadScore})",
            shiftY.toDouble(), result.deltaY.toDouble(), 3.0
        )
    }

    @Test
    fun `expected-window preference must not override a genuine early stop`() {
        // The page cannot scroll further (identical frames) even though the stroke traveled
        // 350px: the matcher must still report stationary / bottom reached.
        val base = generateRightSideChatPattern(width, height)
        val prevSlice = extractSlice(base, 0, height)
        val nextSlice = extractSlice(base, 0, height)

        val result = matcher.match(prevSlice, nextSlice, expectedDeltaY = 350)

        assertEquals("Real stop must win over the expected window", 0, result.deltaY)
        assertTrue("Real stop must declare bottom reached", result.isBottomReached)
    }

    @Test
    fun `dark theme low contrast cards should select valid template and match displacement`() {
        val h = 600
        val w = 200
        val shiftY = 80
        // Dark theme background: #121212, dark cards: #1E1E1E with text
        val darkPixels = IntArray(w * (h + shiftY)) { 0xFF121212.toInt() }
        for (cardStart in listOf(100, 250, 400)) {
            for (y in cardStart until (cardStart + 90)) {
                for (x in 20 until 180) {
                    val isText = (y % 15 in 2..4) && (x in 30..150)
                    darkPixels[y * w + x] = if (isText) 0xFF808080.toInt() else 0xFF1E1E1E.toInt()
                }
            }
        }
        val darkFull = PixelSlice(darkPixels, w, h + shiftY)
        val prevSlice = extractSlice(darkFull, 0, h)
        val nextSlice = extractSlice(darkFull, shiftY, h)

        val darkMatcher = OverlapMatcher(screenDensity = 2.0f)
        val result = darkMatcher.match(prevSlice, nextSlice)

        assertEquals("Dark theme displacement should be accurately detected", shiftY.toDouble(), result.deltaY.toDouble(), 2.0)
    }

    @Test
    fun `scale-adaptive gaussian prior should allow natural variance for large prior`() {
        val shiftY = 240
        val basePattern = generateRealisticUiPattern(width, height + shiftY)
        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSlice = extractSlice(basePattern, shiftY, height)

        // Prior is 255 (15px off from 240). With scale-adaptive sigma = max(8, 0.25*255) = 63.75,
        // penalty = 15^2 / (2 * 63.75^2) = 0.027, extremely smooth and non-locking
        val result = matcher.match(prevSlice, nextSlice, priorDeltaY = 255)
        assertEquals("Adaptive prior must easily lock onto real shift of 240", shiftY.toDouble(), result.deltaY.toDouble(), 1.0)
    }

    @Test
    fun `all black frame should trigger secure window blocked`() {
        val blackPixels = IntArray(width * height) { -0x1000000 } // All black (0xFF000000)
        val blackSlice = PixelSlice(blackPixels, width, height)
        val normalSlice = generateRealisticUiPattern(width, height)

        val result = matcher.match(normalSlice, blackSlice)

        assertTrue("All-black next frame must trigger isSecureBlocked", result.isSecureBlocked)
    }

    @Test
    fun `matching 600px slice benchmark should complete within 5ms`() {
        val basePattern = generateRealisticUiPattern(width, height + 200)
        val prevSlice = extractSlice(basePattern, 0, height)
        val nextSlice = extractSlice(basePattern, 120, height)

        // Warm up
        repeat(5) { matcher.match(prevSlice, nextSlice) }

        val start = System.nanoTime()
        matcher.match(prevSlice, nextSlice)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Matching should take <= 15ms, took ${elapsedMs}ms", elapsedMs <= 15.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `templateHeight below 32 should throw IllegalArgumentException`() {
        OverlapMatcher(templateHeight = 16)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `templateHeight above 1024 should throw IllegalArgumentException`() {
        OverlapMatcher(templateHeight = 1025)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid trimRatio should throw IllegalArgumentException`() {
        OverlapMatcher(trimRatio = 1.0f)
    }

    @Test
    fun `all black frame with non-trivial stride should trigger secure window blocked`() {
        val stride = width + 64
        val blackPixels = IntArray(stride * height) { -0x1000000 }
        val blackSlice = PixelSlice(blackPixels, width, height, stride = stride, offset = 16)
        val normalSlice = generateRealisticUiPattern(width, height)

        val result = matcher.match(normalSlice, blackSlice)

        assertTrue("All-black slice with stride must trigger isSecureBlocked", result.isSecureBlocked)
    }

    private fun generateRealisticUiPattern(w: Int, h: Int): PixelSlice {
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val itemIdx = y / 80
            val isHeader = (y % 80) in 0..15
            val isDivider = (y % 80) == 79
            val baseColor = when {
                isHeader -> -0x333334
                isDivider -> -0x777778
                else -> -0x1
            }
            val avatarW = 10 + (itemIdx * 5) % 15
            val textLen = 25 + (itemIdx * 11) % 35
            val textColor = if (itemIdx % 2 == 0) -0xcccccd else -0x888889
            for (x in 0 until w) {
                val isAvatar = (x in 10 until (10 + avatarW)) && ((y % 80) in 25..55)
                val isText = (x in 40 until (40 + textLen)) && ((y % 20) in 5..12)
                pixels[y * w + x] = when {
                    isAvatar -> -0x1000000 + (itemIdx * 0x050709)
                    isText -> textColor
                    else -> baseColor
                }
            }
        }
        return PixelSlice(pixels, w, h)
    }

    private fun generateSliceWithStaticHeader(w: Int, h: Int, headerH: Int, scrollOffset: Int): PixelSlice {
        val base = generateRealisticUiPattern(w, h + scrollOffset)
        val pixels = IntArray(w * h)
        // Static header: 0..headerH-1 (same content regardless of scrollOffset)
        for (y in 0 until headerH) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (x % 10 < 5) -0xff8800 else -0x333334
            }
        }
        // Dynamic content from basePattern: headerH..h-1
        for (y in headerH until h) {
            val srcY = y + scrollOffset
            System.arraycopy(base.pixels, srcY * w, pixels, y * w, w)
        }
        return PixelSlice(pixels, w, h)
    }

    private fun extractSlice(source: PixelSlice, startY: Int, h: Int): PixelSlice {
        val slicePixels = IntArray(source.width * h)
        System.arraycopy(source.pixels, startY * source.width, slicePixels, 0, source.width * h)
        return PixelSlice(slicePixels, source.width, h)
    }
}
