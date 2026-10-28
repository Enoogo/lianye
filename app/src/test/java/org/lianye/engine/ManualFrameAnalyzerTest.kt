package org.lianye.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lianye.engine.model.PixelSlice

class ManualFrameAnalyzerTest {
    @Test fun `large fixed navigation does not poison scroll overlap`() {
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Forward(150),
            analyzer.analyze(chromeFrame(0), chromeFrame(150)))
    }

    @Test fun `localized animated content preserves unique surrounding overlap`() {
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Forward(100),
            analyzer.analyze(chromeFrame(0, 20), chromeFrame(100, 200)))
    }

    @Test fun `animation alone must not invent forward movement`() {
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        val result = analyzer.analyze(chromeFrame(0, 20), chromeFrame(0, 200))
        org.junit.Assert.assertFalse(result is ManualFrameAnalyzer.Motion.Forward)
    }

    @Test fun `local animation can settle while genuine scrolling cannot`() {
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        org.junit.Assert.assertTrue(analyzer.samePosition(chromeFrame(0, 20), chromeFrame(0, 200)))
        org.junit.Assert.assertFalse(analyzer.samePosition(chromeFrame(0), chromeFrame(100)))
    }

    @Test fun `large playing tile can settle through unchanged surrounding layout`() {
        val first = chromeFrame(0).pixels.clone()
        val second = first.clone()
        for (y in 200 until 380) for (x in 8 until 88) {
            first[y * 96 + x] = 0xFF101080.toInt()
            second[y * 96 + x] = 0xFFE05010.toInt()
        }
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        org.junit.Assert.assertTrue(analyzer.samePosition(PixelSlice(first, 96, 600), PixelSlice(second, 96, 600)))
    }

    @Test fun `temporal animation evidence excludes a changed badge without ignoring stable headings`() {
        val anchor = chromeFrame(0)
        val before = chromeFrame(100)
        val pixels = before.pixels.clone()
        for (y in 300 until 312) for (x in 20 until 52) pixels[y * 96 + x] = 0xFFE03020.toInt()
        val after = PixelSlice(pixels, 96, 600)
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Unmatched, analyzer.analyze(anchor, after))
        assertEquals(ManualFrameAnalyzer.Motion.Forward(100),
            analyzer.analyze(anchor, after, candidateChanges = analyzer.changedRegions(before, after)))
    }

    @Test fun `large temporal video leaves enough unique content to prove the overlap`() {
        val anchor = chromeFrame(0)
        val next = chromeFrame(150)
        val beforePixels = next.pixels.clone(); val afterPixels = next.pixels.clone()
        for (y in 200 until 330) for (x in 0 until 96) {
            beforePixels[y * 96 + x] = 0xFF102040.toInt()
            afterPixels[y * 96 + x] = 0xFFD09020.toInt()
        }
        val before = PixelSlice(beforePixels, 96, 600)
        val after = PixelSlice(afterPixels, 96, 600)
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Forward(150),
            analyzer.analyze(anchor, after, candidateChanges = analyzer.changedRegions(before, after)))
    }

    @Test fun `a large video cut does not report a gap while unmasked content remains exactly in place`() {
        fun video(color: Int): PixelSlice {
            val data = chromeFrame(0).pixels.clone()
            for (y in 220 until 460) for (x in 0 until 96) data[y * 96 + x] = color
            return PixelSlice(data, 96, 600)
        }
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        val anchor = video(0xFF202060.toInt())
        val observed = video(0xFFB08020.toInt())
        val candidate = video(0xFF206020.toInt())
        val changes = analyzer.changedRegions(anchor, observed)
        assertEquals(ManualFrameAnalyzer.Motion.Stationary,
            analyzer.analyze(anchor, candidate, anchorChanges = changes))
        val wrongHeading = candidate.pixels.clone()
        for (y in 170 until 186) for (x in 20 until 72) wrongHeading[y * 96 + x] = 0xFFD01030.toInt()
        org.junit.Assert.assertFalse("Animation masks must not hide an unrelated changed heading",
            analyzer.analyze(anchor, PixelSlice(wrongHeading, 96, 600), anchorChanges = changes) == ManualFrameAnalyzer.Motion.Stationary)
    }

    private fun chromeFrame(offset: Int, animation: Int? = null): PixelSlice {
        val width = 96
        val height = 600
        return PixelSlice(IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val documentY = y - 150 + offset
            val level = when {
                y < 150 -> 40 + (x * 13 + y * 7) % 150
                y >= 510 -> 30 + (x * 11 + y * 3) % 180
                animation != null && documentY in 190..260 && x < 55 -> animation
                else -> 30 + ((documentY * 7919 xor (documentY * documentY * 31) xor (x * 199)) and 0x7FFFFFFF) % 190
            }
            0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
        }, width, height)
    }

    @Test fun `collapsing header and translucent footer exclude stationary chrome from local evidence`() {
        fun frame(header: Int, offset: Int, clock: Int): PixelSlice {
            val width = 96; val height = 600
            return PixelSlice(IntArray(width * height) { index ->
                val x = index % width; val y = index / width
                val docY = y - header + offset
                val level = when {
                    y in 10..25 && x in 8..35 -> clock
                    y < header -> 40 + (x * 13 + y * 7) % 150
                    y > 560 && x % 20 < 5 -> 20
                    else -> 30 + ((docY * 7919 xor (docY * docY * 31) xor (x * 199)) and 0x7FFFFFFF) % 190
                }
                0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
            }, width, height)
        }
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Forward(250), analyzer.analyze(frame(150, 0, 20), frame(60, 160, 200)))
    }

    @Test fun `repeated paragraph texture cannot select a smaller false displacement`() {
        val width = 96
        val height = 600
        fun frame(offset: Int) = PixelSlice(IntArray(width * height) { index ->
            val x = index % width
            val documentY = index / width + offset
            val row = documentY % 200
            if (row in 84..96 && x in 12..84) {
                val level = (documentY / 200 * 47 + 20) % 200
                0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
            } else {
                val level = if (row % 50 < 15) 80 else if (row < 100) 235 else 190
                0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
            }
        }, width, height)
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Forward(250), analyzer.analyze(frame(0), frame(250)))
    }

    @Test fun `ambiguous forward and reverse overlaps refuse to guess a seam`() {
        val width = 96
        val height = 600
        fun frame(offset: Int) = PixelSlice(IntArray(width * height) { index ->
            val row = (index / width + offset) % 200
            val level = if (row % 50 < 15) 80 else if (row < 100) 235 else 190
            0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
        }, width, height)
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Unmatched, analyzer.analyze(frame(0), frame(250)))
    }

    @Test fun `identical periodic rows are stationary without a gesture displacement prior`() {
        val width = 96
        val height = 600
        val pixels = IntArray(width * height) { index ->
            if ((index / width) % 50 < 12) 0xFF111111.toInt() else 0xFFEEEEEE.toInt()
        }
        val frame = PixelSlice(pixels, width, height)
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(templateHeight = 100, maxSearchRange = 300))
        assertEquals(ManualFrameAnalyzer.Motion.Stationary, analyzer.analyze(frame, frame))
    }
}
