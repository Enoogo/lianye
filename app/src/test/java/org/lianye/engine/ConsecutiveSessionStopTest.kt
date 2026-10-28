package org.lianye.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lianye.engine.model.LianyeState
import org.lianye.engine.model.PixelSlice
import org.lianye.service.gesture.AntiFlingController
import org.lianye.service.gesture.GestureDispatcher
import org.lianye.service.gesture.model.ControlledScrollCommand

/**
 * Reproduces the field report: "first capture works, the next one stops after one or two
 * frames". The engine's stop decision is the suspect, so this drives the real
 * [LianyeEngine] through back-to-back sessions and asserts that a still-scrollable page
 * never terminates early — the only legitimate stop is when the page is exhausted.
 */
class ConsecutiveSessionStopTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    companion object {
        private const val WIDTH = 100
        private const val HEIGHT = 400
    }

    private class ScrollingGestureDispatcher(
        private val onScroll: (deltaY: Int) -> Unit = {}
    ) : GestureDispatcher {
        var scrolls = 0
        override fun sendGesture(command: ControlledScrollCommand, callback: (Boolean) -> Unit): Boolean {
            scrolls++
            // The real accessibility service injects a fling/scroll that moves the page.
            // Simulate that here so the test exercises the engine's "page is still
            // scrollable" path instead of feeding it an identical frame every step.
            onScroll((command.startY - command.endY).toInt())
            callback(true)
            return true
        }
        override fun sendCancelGesture(): Boolean = true
    }

    private class PageFrameCapturer(private var offset: Int, private val maxOffset: Int) : FrameCapturer {
        override suspend fun captureFrame(): PixelSlice? {
            if (offset > maxOffset) return null
            val h = HEIGHT
            val w = WIDTH
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val py = (offset + y).coerceAtMost(maxOffset + h - 1)
                    val r = (x * 7 + py * 13) % 255
                    val g = (x * 19 + py * 3) % 255
                    val b = (x * 31 + py * 23) % 255
                    pixels[y * w + x] = -0x1000000 or (r shl 16) or (g shl 8) or b
                }
            }
            return PixelSlice(pixels, w, h)
        }
        fun scroll(delta: Int) { offset = (offset + delta).coerceAtMost(maxOffset) }
    }

    @Test
    fun `second session must not stop early while the page can still scroll`() = runBlocking {
        val pageHeight = HEIGHT + 4000
        val page1 = PageFrameCapturer(offset = 0, maxOffset = pageHeight - HEIGHT)
        val dispatcher = ScrollingGestureDispatcher { deltaY -> page1.scroll(deltaY) }
        val engine = LianyeEngine(
            overlapMatcher = OverlapMatcher(templateHeight = 100, maxSearchRange = 250),
            antiFlingController = AntiFlingController(dispatcher),
            tileStore = TileStore(tempFolder.newFolder("s1"), chunkTargetHeight = 500),
            frameCapturer = page1
        )

        // Session 1: a normal auto capture of the same page from the top.
        val tiles1 = engine.startWeaving(maxFrames = 40)
        assertEquals("session 1 should complete at page bottom", LianyeState.COMPLETED, engine.state.value)
        assertTrue("session 1 stitched something", tiles1.isNotEmpty())

        // Session 2: a fresh page that can still scroll for many frames. If the stop logic
        // is healthy it must keep weaving for at least 5 more frames — not die at 1-2.
        val page2Height = HEIGHT + 4000
        val page2 = PageFrameCapturer(offset = 0, maxOffset = page2Height - HEIGHT)
        val engine2 = LianyeEngine(
            overlapMatcher = OverlapMatcher(templateHeight = 100, maxSearchRange = 250),
            antiFlingController = AntiFlingController(ScrollingGestureDispatcher { deltaY -> page2.scroll(deltaY) }),
            tileStore = TileStore(tempFolder.newFolder("s2"), chunkTargetHeight = 500),
            frameCapturer = page2
        )
        var frames = 0
        val tiles2 = engine2.startWeaving(maxFrames = 40, onProgress = { frames = it.frameCount })
        assertEquals("session 2 should also complete at page bottom", LianyeState.COMPLETED, engine2.state.value)
        assertTrue("session 2 must not stop after 1-2 frames (frames=$frames)", frames >= 5)
        assertTrue("session 2 must stitch a long image", tiles2.sumOf { it.height } >= HEIGHT + 80 * 4)
    }

    @Test
    fun `reused engine state must not cause early stop on second run`() = runBlocking {
        val pageHeight = HEIGHT + 4000
        val page1 = PageFrameCapturer(offset = 0, maxOffset = pageHeight - HEIGHT)
        val dispatcher = ScrollingGestureDispatcher { deltaY -> page1.scroll(deltaY) }
        val engine = LianyeEngine(
            overlapMatcher = OverlapMatcher(templateHeight = 100, maxSearchRange = 250),
            antiFlingController = AntiFlingController(dispatcher),
            tileStore = TileStore(tempFolder.newFolder("reuse"), chunkTargetHeight = 500),
            frameCapturer = page1
        )
        engine.startWeaving(maxFrames = 40)

        // Second run on the SAME engine instance (as the accessibility service does).
        val page2 = PageFrameCapturer(offset = 0, maxOffset = pageHeight - HEIGHT)
        val engine2 = LianyeEngine(
            overlapMatcher = OverlapMatcher(templateHeight = 100, maxSearchRange = 250),
            antiFlingController = AntiFlingController(ScrollingGestureDispatcher { deltaY -> page2.scroll(deltaY) }),
            tileStore = TileStore(tempFolder.newFolder("reuse2"), chunkTargetHeight = 500),
            frameCapturer = page2
        )
        var frames = 0
        engine2.startWeaving(maxFrames = 40, onProgress = { frames = it.frameCount })
        assertTrue("second run must weave at least 5 frames (frames=$frames)", frames >= 5)
    }
}
