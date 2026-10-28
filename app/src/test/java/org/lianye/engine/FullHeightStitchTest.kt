package org.lianye.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lianye.engine.model.PixelSlice
import org.lianye.engine.model.TileMetadata
import org.lianye.service.gesture.AntiFlingController
import org.lianye.service.gesture.GestureDispatcher
import org.lianye.service.gesture.model.ControlledScrollCommand
import java.io.File
import java.nio.file.Files
import kotlin.math.min

/**
 * Drives the real LianyeEngine against a synthetic scrolling page and verifies the stitched
 * pixels row by row. This is the closest thing to a device test that runs without an emulator.
 */
class FullHeightStitchTest {

    private val width = 1080
    private val screenHeight = 2400
    private val bandTop = 300
    private val bandBottom = 2100
    private val step = 700

    private class Sim(
        val page: IntArray,
        val pageHeight: Int,
        val width: Int,
        val screenHeight: Int,
        val step: Int,
        val bandTop: Int,
        val bandBottom: Int,
        var fullHeight: Boolean,
        val barHeight: Int = 0
    ) {
        var scroll = 0
        val maxScroll: Int get() = (pageHeight - screenHeight).coerceAtLeast(0)
        fun advance() { scroll = min(maxScroll, scroll + step) }
        fun pixel(x: Int, y: Int): Int = page[y * width + x]
    }

    private class SimDispatcher(private val sim: Sim) : GestureDispatcher {
        override fun sendGesture(command: ControlledScrollCommand, callback: (Boolean) -> Unit): Boolean {
            sim.advance()
            callback(true)
            return true
        }
        override fun sendCancelGesture(): Boolean = true
    }

    private class SimCapturer(private val sim: Sim) : FrameCapturer {
        private var span = CaptureSpan.BAND

        override val contentTopInScreen: Int get() = sim.bandTop
        override val fullHeightMode: Boolean get() = sim.fullHeight
        override fun setCaptureSpan(span: CaptureSpan) { this.span = span }

        override suspend fun captureFrame(): PixelSlice? {
            val top = when (span) {
                CaptureSpan.BAND -> sim.bandTop
                CaptureSpan.FIRST, CaptureSpan.TOP_ONLY -> 0
                CaptureSpan.BOTTOM_ONLY -> sim.bandBottom
            }
            val bottom = when (span) {
                CaptureSpan.BAND -> sim.bandBottom
                CaptureSpan.FIRST -> sim.bandBottom
                CaptureSpan.TOP_ONLY -> sim.bandTop
                CaptureSpan.BOTTOM_ONLY -> sim.screenHeight
            }
            if (bottom <= top) return null
            val h = bottom - top
            val pixels = IntArray(sim.width * h)
            for (y in 0 until h) {
                val screenY = top + y
                val pageY = (sim.scroll + screenY).coerceIn(0, sim.pageHeight - 1)
                val src = pageY * sim.width
                System.arraycopy(sim.page, src, pixels, y * sim.width, sim.width)
                if (sim.barHeight > 0 && screenY >= sim.screenHeight - sim.barHeight) {
                    java.util.Arrays.fill(pixels, y * sim.width, (y + 1) * sim.width, 0xFF303030.toInt())
                }
            }
            return PixelSlice(pixels, sim.width, h)
        }

        override fun prepareSession() {}
        override fun releaseSession() {}
    }

    private fun makePage(height: Int): IntArray {
        // Every row gets its own pseudo-random colour so no two rows repeat and the matcher
        // cannot lock onto a periodic pattern (which real pages rarely have).
        val page = IntArray(width * height)
        val random = java.util.Random(0x5EED)
        for (y in 0 until height) {
            val base = 0xFF000000.toInt() or (random.nextInt() and 0x00FFFFFF)
            for (x in 0 until width) {
                page[y * width + x] = base
            }
            page[y * width + (y % width)] = 0xFFFFFFFF.toInt()
        }
        return page
    }

    private fun runSession(sim: Sim, dir: File): Pair<List<TileMetadata>, LianyeEngine> {
        val store = TileStore(dir)
        val engine = LianyeEngine(
            overlapMatcher = OverlapMatcher(screenDensity = 2.5f),
            antiFlingController = AntiFlingController(SimDispatcher(sim)),
            tileStore = store,
            frameCapturer = SimCapturer(sim)
        )
        val tiles = runBlocking { engine.startWeaving() }
        return tiles to engine
    }

    /** Asserts the stitched tile rows match the source page rows [expectedStart, expectedStart+expectedHeight). */
    private fun assertStitched(tiles: List<TileMetadata>, page: IntArray, pageHeight: Int, expectedStart: Int, expectedHeight: Int) {
        assertTrue("no tiles produced", tiles.isNotEmpty())
        val totalHeight = tiles.sumOf { it.height }
        assertEquals("stitched height", expectedHeight, totalHeight)

        var imageRow = 0
        for (tile in tiles.sortedBy { it.startY }) {
            val bytes = tile.file.readBytes()
            assertEquals(tile.width * tile.height * 4, bytes.size)
            for (row in 0 until tile.height) {
                val expectedPageRow = expectedStart + imageRow
                for (x in intArrayOf(0, width / 3, width / 2, width - 1)) {
                    val offset = (row * tile.width + x) * 4
                    val a = bytes[offset].toInt() and 0xFF
                    val r = bytes[offset + 1].toInt() and 0xFF
                    val g = bytes[offset + 2].toInt() and 0xFF
                    val b = bytes[offset + 3].toInt() and 0xFF
                    val actual = (a shl 24) or (r shl 16) or (g shl 8) or b
                    val expected = page[expectedPageRow.coerceIn(0, pageHeight - 1) * width + x]
                    assertEquals(
                        "pixel mismatch at image row $imageRow (page row $expectedPageRow), x=$x",
                        expected, actual
                    )
                }
                imageRow++
            }
        }
    }

    @Test
    fun bandOnly_session_isContiguous() {
        val pageHeight = 9000
        val sim = Sim(makePage(pageHeight), pageHeight, width, screenHeight, step, bandTop, bandBottom, fullHeight = false)
        val dir = Files.createTempDirectory("lianye-band").toFile()
        val (tiles, _) = runSession(sim, dir)

        val expectedStart = bandTop
        val expectedHeight = (sim.scroll + bandBottom) - bandTop
        assertStitched(tiles, sim.page, pageHeight, expectedStart, expectedHeight)
        dir.deleteRecursively()
    }

    @Test
    fun fullHeight_session_startsWithTopAndEndsWithBottom() {
        val pageHeight = 9000
        val sim = Sim(makePage(pageHeight), pageHeight, width, screenHeight, step, bandTop, bandBottom, fullHeight = true)
        val dir = Files.createTempDirectory("lianye-full").toFile()
        val (tiles, _) = runSession(sim, dir)

        // Starts at screen row 0 and ends at the last captured screen bottom.
        val expectedStart = 0
        val expectedHeight = sim.scroll + screenHeight
        assertStitched(tiles, sim.page, pageHeight, expectedStart, expectedHeight)
        dir.deleteRecursively()
    }

    @Test
    fun fullHeight_secondSession_isNotPollutedByFirst() {
        val pageHeight = 9000
        val sim = Sim(makePage(pageHeight), pageHeight, width, screenHeight, step, bandTop, bandBottom, fullHeight = true)
        val dir = Files.createTempDirectory("lianye-twice").toFile()

        val (firstTiles, engine) = runSession(sim, dir)
        assertStitched(firstTiles, sim.page, pageHeight, 0, sim.scroll + screenHeight)

        // Second run from the top, same engine instance (mirrors reusing the service engine).
        sim.scroll = 0
        val store = TileStore(dir)
        val engine2 = LianyeEngine(
            overlapMatcher = OverlapMatcher(screenDensity = 2.5f),
            antiFlingController = AntiFlingController(SimDispatcher(sim)),
            tileStore = store,
            frameCapturer = SimCapturer(sim)
        )
        val secondTiles = runBlocking { engine2.startWeaving() }
        assertStitched(secondTiles, sim.page, pageHeight, 0, sim.scroll + screenHeight)
        dir.deleteRecursively()
    }

    @Test
    fun fullHeight_withStaticBottomBar_cropsBarEveryFrame() {
        val pageHeight = 9000
        val bar = 150
        // Band reaches the screen bottom, so the fixed bar sits inside the captured band.
        val sim = Sim(makePage(pageHeight), pageHeight, width, screenHeight, step,
            bandTop, screenHeight, fullHeight = true, barHeight = bar)
        val dir = Files.createTempDirectory("lianye-bar").toFile()
        val (tiles, _) = runSession(sim, dir)

        // Content is stitched without the bar; the fixed bar is appended exactly once.
        val contentHeight = sim.scroll + (screenHeight - bar)
        assertEquals("total height", contentHeight + bar, tiles.sumOf { it.height })
        assertStitchedPrefix(tiles, sim.page, pageHeight, 0, contentHeight, contentHeight + bar)
        dir.deleteRecursively()
    }

    @Test
    fun fullHeight_shortPage_stopsAtBottom() {
        val pageHeight = 3000
        val sim = Sim(makePage(pageHeight), pageHeight, width, screenHeight, step,
            bandTop, bandBottom, fullHeight = true)
        val dir = Files.createTempDirectory("lianye-short").toFile()
        val (tiles, _) = runSession(sim, dir)

        assertEquals("captured everything", pageHeight, tiles.sumOf { it.height })
        assertStitched(tiles, sim.page, pageHeight, 0, pageHeight)
        dir.deleteRecursively()
    }

    @Test
    fun debug_detect_bottom_bar() {
        val pageHeight = 9000
        val bar = 150
        val sim = Sim(makePage(pageHeight), pageHeight, width, screenHeight, step,
            bandTop, screenHeight, fullHeight = true, barHeight = bar)
        val capturer = SimCapturer(sim)
        val first = runBlocking { capturer.captureFrame() }!!
        sim.advance()
        val second = runBlocking { capturer.captureFrame() }!!
        val engine = LianyeEngine(
            OverlapMatcher(screenDensity = 2.5f),
            AntiFlingController(SimDispatcher(sim)),
            TileStore(Files.createTempDirectory("lianye-dbg").toFile()),
            capturer
        )
        val detected = engine.detectBottomBarHeight(first, second, step)
        println("DETECTED_BAR=$detected")
        assertEquals(bar, detected)
    }

    @Test
    fun fullHeight_bottomStrip_isOnlyTheAreaBelowTheBand() {
        val pageHeight = 9000
        val bandBottom = 2100
        val sim = Sim(makePage(pageHeight), pageHeight, width, screenHeight, step,
            bandTop, bandBottom, fullHeight = true, barHeight = 150)
        val dir = Files.createTempDirectory("lianye-strip").toFile()
        val (tiles, _) = runSession(sim, dir)

        // The very last tile must be the below-band strip, not a full screen.
        assertEquals("last strip height", screenHeight - bandBottom, tiles.last().height)
        assertEquals("total height", sim.scroll + screenHeight, tiles.sumOf { it.height })
        dir.deleteRecursively()
    }

    /** Verifies the first [contentHeight] rows against the page and the rest as the static bar. */
    private fun assertStitchedPrefix(
        tiles: List<TileMetadata>,
        page: IntArray,
        pageHeight: Int,
        expectedStart: Int,
        contentHeight: Int,
        totalHeight: Int
    ) {
        assertEquals(totalHeight, tiles.sumOf { it.height })
        var imageRow = 0
        for (tile in tiles.sortedBy { it.startY }) {
            val bytes = tile.file.readBytes()
            for (row in 0 until tile.height) {
                val x = width / 2
                val offset = (row * tile.width + x) * 4
                val a = bytes[offset].toInt() and 0xFF
                val r = bytes[offset + 1].toInt() and 0xFF
                val g = bytes[offset + 2].toInt() and 0xFF
                val b = bytes[offset + 3].toInt() and 0xFF
                val actual = (a shl 24) or (r shl 16) or (g shl 8) or b
                if (imageRow < contentHeight) {
                    val expected = page[(expectedStart + imageRow).coerceIn(0, pageHeight - 1) * width + x]
                    assertEquals("content mismatch at row $imageRow", expected, actual)
                } else {
                    assertEquals("bar mismatch at row $imageRow", 0xFF303030.toInt(), actual)
                }
                imageRow++
            }
        }
    }

}
