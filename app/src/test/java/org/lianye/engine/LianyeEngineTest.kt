package org.lianye.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lianye.engine.model.LianyeState
import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.CaptureProgress
import org.lianye.engine.model.PixelSlice
import org.lianye.service.gesture.AntiFlingController
import org.lianye.service.gesture.GestureDispatcher
import org.lianye.service.gesture.model.ControlledScrollCommand

@OptIn(ExperimentalCoroutinesApi::class)
class LianyeEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var overlapMatcher: OverlapMatcher
    private lateinit var antiFlingController: AntiFlingController
    private lateinit var tileStore: TileStore
    private val width = 100
    private val height = 400

    private class FakeGestureDispatcher : GestureDispatcher {
        var lastCommand: ControlledScrollCommand? = null
        var cancelGestureCalled = false

        override fun sendGesture(command: ControlledScrollCommand, callback: (Boolean) -> Unit): Boolean {
            lastCommand = command
            callback(true)
            return true
        }

        override fun sendCancelGesture(): Boolean {
            cancelGestureCalled = true
            return true
        }
    }

    private class FakeFrameCapturer(private val frames: List<PixelSlice>) : FrameCapturer {
        private var index = 0
        override suspend fun captureFrame(): PixelSlice? {
            return if (index < frames.size) frames[index++] else null
        }
    }

    @Before
    fun setUp() {
        overlapMatcher = OverlapMatcher(templateHeight = 100, maxSearchRange = 250)
        antiFlingController = AntiFlingController(FakeGestureDispatcher())
        tileStore = TileStore(tempFolder.root, chunkTargetHeight = 500)
    }

    @Test
    fun `normal capture loop should stitch frames and finalize tiles`() = runTest {
        val shiftY = 80
        val basePattern = generatePattern(width, height + shiftY * 3)
        val frame0 = extractSlice(basePattern, 0, height)
        val frame1 = extractSlice(basePattern, shiftY, height)
        val frame2 = extractSlice(basePattern, shiftY * 2, height)

        val capturer = FakeFrameCapturer(listOf(frame0, frame1, frame2))
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)

        val tiles = engine.startWeaving(maxFrames = 3)

        assertEquals("Engine state should be COMPLETED", LianyeState.COMPLETED, engine.state.value)
        assertFalse("Tiles should not be empty", tiles.isEmpty())
        val totalHeight = tiles.sumOf { it.height }
        // Frame 0 is base (height), Frame 1 adds shiftY, Frame 2 adds shiftY
        assertEquals("Total assembled height", height + shiftY * 2, totalHeight)
    }

    @Test
    fun `bottom reached should terminate early and finalize tiles`() = runTest {
        val basePattern = generatePattern(width, height + 100)
        val frame0 = extractSlice(basePattern, 0, height)
        val frame1 = extractSlice(basePattern, 50, height)
        val frame2 = extractSlice(basePattern, 50, height) // Identical frame (0 shift) -> isBottomReached

        val capturer = FakeFrameCapturer(listOf(frame0, frame1, frame2))
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)

        val tiles = engine.startWeaving(maxFrames = 10)

        assertEquals("Engine state should be COMPLETED", LianyeState.COMPLETED, engine.state.value)
        assertEquals("Total height should include initial frame + frame1 increment only", height + 50, tiles.sumOf { it.height })
    }

    @Test
    fun `consecutive stationary frames should terminate with completed state`() = runTest {
        val basePattern = generatePattern(width, height)
        val frame0 = extractSlice(basePattern, 0, height)
        val frame1 = extractSlice(basePattern, 0, height) // Stationary frame 1
        val frame2 = extractSlice(basePattern, 0, height) // Stationary frame 2

        val capturer = FakeFrameCapturer(listOf(frame0, frame1, frame2))
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)

        val tiles = engine.startWeaving(maxFrames = 10)

        assertEquals("Engine state should be COMPLETED", LianyeState.COMPLETED, engine.state.value)
        assertFalse("Tiles should not be empty", tiles.isEmpty())
    }

    @Test
    fun `secure blocked frame should transition to ERROR and stop`() = runTest {
        val normalFrame = generatePattern(width, height)
        val blackPixels = IntArray(width * height) { -0x1000000 }
        val secureFrame = PixelSlice(blackPixels, width, height)

        val capturer = FakeFrameCapturer(listOf(normalFrame, secureFrame))
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)

        val tiles = engine.startWeaving(maxFrames = 5)

        assertEquals("Engine state should be ERROR", LianyeState.ERROR, engine.state.value)
        // Initial frame should be preserved in error so user doesn't lose what was already captured
        assertFalse("Initial captured tiles should be preserved on error", tiles.isEmpty())
    }

    @Test
    fun `detectBottomBarHeight should accurately detect stationary footer`() {
        val testH = 600
        val testW = 100
        val footerH = 80
        val deltaY = 80

        val slice0 = generateSliceWithFixedBottom(testW, testH, footerH, scrollOffset = 0)
        val slice1 = generateSliceWithFixedBottom(testW, testH, footerH, scrollOffset = deltaY)

        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, FakeFrameCapturer(emptyList()))
        val detected = engine.detectBottomBarHeight(slice0, slice1, deltaY)

        assertEquals("Detected bottom bar height should match exactly", footerH, detected)
    }

    @Test
    fun `weaving app with fixed bottom bar should append footer only once at end`() = runTest {
        val testH = 600
        val testW = 100
        val footerH = 80
        val shiftY = 80

        val slice0 = generateSliceWithFixedBottom(testW, testH, footerH, scrollOffset = 0)
        val slice1 = generateSliceWithFixedBottom(testW, testH, footerH, scrollOffset = shiftY)
        val slice2 = generateSliceWithFixedBottom(testW, testH, footerH, scrollOffset = shiftY * 2)

        val capturer = FakeFrameCapturer(listOf(slice0, slice1, slice2))
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)

        val tiles = engine.startWeaving(maxFrames = 3)

        assertEquals("Engine state should be COMPLETED", LianyeState.COMPLETED, engine.state.value)
        // Frame 0 body (testH - footerH = 520) + Frame 1 (shiftY = 80) + Frame 2 (shiftY = 80) + Final Footer (footerH = 80) = 760
        val totalHeight = tiles.sumOf { it.height }
        assertEquals("Total assembled height with single footer", (testH - footerH) + shiftY * 2 + footerH, totalHeight)
    }

    @Test
    fun `stop after initial progress preserves single screen with user stopped reason`() = runTest {
        val frame = generatePattern(width, height)
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, FakeFrameCapturer(listOf(frame)))
        val progress = mutableListOf<CaptureProgress>()
        val tiles = engine.startWeaving(onProgress = {
            progress.add(it)
            engine.stop()
        })
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
        assertEquals(LianyeState.COMPLETED, engine.state.value)
        assertEquals(height, tiles.sumOf { it.height })
        assertEquals(CaptureProgress(1, height, height), progress.first())
    }

    @Test
    fun `service stop preserves image and reports permission loss instead of user completion`() = runTest {
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore,
            FakeFrameCapturer(listOf(generatePattern(width, height))))
        val tiles = engine.startWeaving(onProgress = { engine.stop(CaptureCompletion.PERMISSION_LOST) })
        assertEquals(CaptureCompletion.PERMISSION_LOST, engine.completionReason)
        assertEquals(LianyeState.ERROR, engine.state.value)
        assertEquals(height, tiles.sumOf { it.height })
    }

    @Test
    fun `default capture continues beyond fifty frames and reports actual stitched height`() = runTest {
        val shift = 80
        val base = generatePattern(width, height + shift * 55)
        val capturer = FakeFrameCapturer((0..54).map { extractSlice(base, it * shift, height) })
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)
        var latest = CaptureProgress(0, 0, 0)
        val tiles = engine.startWeaving(onProgress = {
            latest = it
            if (it.frameCount >= 52) engine.stop()
        })
        assertEquals(52, latest.frameCount)
        assertEquals(height + 51 * shift, latest.stitchedHeightPx)
        assertEquals(latest.stitchedHeightPx, tiles.sumOf { it.height })
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
    }

    @Test
    fun `capturer exception preserves valid initial frame instead of clearing or throwing`() = runTest {
        val frame = generatePattern(width, height)
        val capturer = object : FrameCapturer {
            var calls = 0
            override suspend fun captureFrame(): PixelSlice? {
                if (calls++ == 0) return frame
                error("capture failed")
            }
        }
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)
        val tiles = engine.startWeaving()
        assertEquals(CaptureCompletion.INTERRUPTED, engine.completionReason)
        assertEquals(LianyeState.ERROR, engine.state.value)
        assertEquals(height, tiles.sumOf { it.height })
        assertTrue(tiles.all { it.file.exists() })
    }

    @Test
    fun `permission loss after stitching preserves partial output and reports distinct reason`() = runTest {
        val base = generatePattern(width, height + 80)
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore,
            FakeFrameCapturer(listOf(extractSlice(base, 0, height), extractSlice(base, 80, height))))
        val tiles = engine.startWeaving()
        assertEquals(CaptureCompletion.PERMISSION_LOST, engine.completionReason)
        assertEquals(LianyeState.ERROR, engine.state.value)
        assertEquals(height + 80, tiles.sumOf { it.height })
    }

    @Test
    fun `storage reserve stops scrolling but retains initial screen`() = runTest {
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore,
            FakeFrameCapturer(listOf(generatePattern(width, height))), minimumFreeStorageBytes = Long.MAX_VALUE)
        val tiles = engine.startWeaving()
        assertEquals(CaptureCompletion.STORAGE_FULL, engine.completionReason)
        assertEquals(height, tiles.sumOf { it.height })
    }

    @Test
    fun `coroutine cancellation commits recoverable rows and releases capture session`() = runTest {
        var released = false
        val capturer = object : FrameCapturer {
            override suspend fun captureFrame() = generatePattern(width, height)
            override fun releaseSession() { released = true }
        }
        val engine = LianyeEngine(overlapMatcher, antiFlingController, tileStore, capturer)
        val job = launch { engine.startWeaving() }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(CaptureCompletion.INTERRUPTED, engine.completionReason)
        assertEquals(height, tileStore.committedTiles().sumOf { it.height })
        assertTrue(released)
    }

    private fun generateSliceWithFixedBottom(w: Int, h: Int, footerH: Int, scrollOffset: Int): PixelSlice {
        val base = generatePattern(w, h + scrollOffset + footerH)
        val pixels = IntArray(w * h)
        // Body (0 until h - footerH): scrolls with scrollOffset
        for (y in 0 until (h - footerH)) {
            val srcY = y + scrollOffset
            System.arraycopy(base.pixels, srcY * w, pixels, y * w, w)
        }
        // Fixed footer (h - footerH until h): stationary across all frames
        for (y in (h - footerH) until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (x % 8 < 4) -0x5500aa else -0x223344
            }
        }
        return PixelSlice(pixels, w, h)
    }

    private fun generatePattern(w: Int, h: Int): PixelSlice {
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val r = (x * 7 + y * 13) % 255
                val g = (x * 19 + y * 3) % 255
                val b = (x * 31 + y * 23) % 255
                pixels[y * w + x] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }
        return PixelSlice(pixels, w, h)
    }

    private fun extractSlice(source: PixelSlice, startY: Int, h: Int): PixelSlice {
        val slicePixels = IntArray(source.width * h)
        System.arraycopy(source.pixels, startY * source.width, slicePixels, 0, source.width * h)
        return PixelSlice(slicePixels, source.width, h)
    }
}
