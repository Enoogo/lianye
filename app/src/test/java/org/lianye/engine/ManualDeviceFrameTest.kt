package org.lianye.engine

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lianye.engine.model.PixelSlice

/** Generated offline ScrollView content captured on Android 10; contains no personal app content. */
class ManualDeviceFrameTest {
    @Test fun skippedNativeParagraphsCannotBeJoinedThroughRepeatedText() {
        fun frame(name: String): PixelSlice =
            DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/$name.argb.gz"))).use { input ->
                val width = input.readInt(); val height = input.readInt()
                PixelSlice(IntArray(width * height) { input.readInt() }, width, height)
            }
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(screenDensity = 3f))
        assertEquals(ManualFrameAnalyzer.Motion.Unmatched,
            analyzer.analyze(frame("manual-chrome-candidate"), frame("manual-chrome-gap")))
    }

    @Test fun animatedRepeatedParagraphsWithLargeChromeKeepTheirActualDirection() {
        fun frame(name: String): PixelSlice =
            DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/$name.argb.gz"))).use { input ->
                val width = input.readInt(); val height = input.readInt()
                PixelSlice(IntArray(width * height) { input.readInt() }, width, height)
            }
        val analyzer = ManualFrameAnalyzer(OverlapMatcher(screenDensity = 3f))
        val result = analyzer.analyze(frame("manual-chrome-anchor"), frame("manual-chrome-candidate"))
        // Section 3 ends at y=1733 before the swipe and y=906 afterwards.
        assertEquals(analyzer.lastDecisionDetails, ManualFrameAnalyzer.Motion.Forward(827), result)
    }

    @Test fun repeatedNativeParagraphsJoinInTheActualForwardDirection() {
        fun frame(name: String): PixelSlice {
            return DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/$name.argb.gz"))).use { input ->
                val width = input.readInt()
                val height = input.readInt()
                PixelSlice(IntArray(width * height) { input.readInt() }, width, height)
            }
        }
        val result = ManualFrameAnalyzer(OverlapMatcher(screenDensity = 3f))
            .analyze(frame("manual-anchor"), frame("manual-candidate"))
        // Section 3's background starts at y=1634 in the anchor and y=442 after the swipe.
        assertEquals(ManualFrameAnalyzer.Motion.Forward(1192), result)
    }
}
