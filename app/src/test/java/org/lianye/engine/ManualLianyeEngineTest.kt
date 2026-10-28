package org.lianye.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.CaptureProgress
import org.lianye.engine.model.LianyeState
import org.lianye.engine.model.PixelSlice
import org.lianye.engine.model.TileMetadata
import org.lianye.service.gesture.AntiFlingController
import org.lianye.service.gesture.GestureDispatcher
import org.lianye.service.gesture.model.ControlledScrollCommand
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ManualLianyeEngineTest {
    @get:Rule val temp = TemporaryFolder()
    private val width = 96
    private val height = 400

    private class Samples(frames: List<PixelSlice>, stabilizeFirst: Boolean = true) : FrameCapturer {
        private val frames = if (stabilizeFirst && frames.isNotEmpty()) listOf(frames.first()) + frames else frames
        var sampleCalls = 0
        var cleanCalls = 0
        var released = false
        var onSample: (Int) -> Unit = {}
        var onClean: (Int) -> Unit = {}
        var cleanFrames: List<PixelSlice>? = null
        override suspend fun captureFrame(): PixelSlice? = error("Manual must use sampleFrame")
        override suspend fun sampleFrame(): FrameSample {
            val index = sampleCalls++
            onSample(index)
            return frames.getOrNull(index)?.let(FrameSample::Available) ?: FrameSample.NoNewFrame
        }
        override suspend fun captureCleanFrame(candidate: PixelSlice): FrameSample {
            val index = cleanCalls++
            onClean(index)
            return FrameSample.Available(cleanFrames?.getOrNull(index) ?: candidate)
        }
        override fun releaseSession() { released = true }
    }

    private fun engine(capturer: FrameCapturer, controller: AntiFlingController? = null,
                       reserve: Long = 0): LianyeEngine = LianyeEngine(
        OverlapMatcher(templateHeight = 100, maxSearchRange = 250), controller,
        TileStore(temp.root, chunkTargetHeight = 500), capturer, minimumFreeStorageBytes = reserve)

    @Test fun `stationary and absent buffers remain active without increasing progress`() = runTest {
        val first = pattern(height)
        val capturer = Samples(List(8) { first })
        val engine = engine(capturer)
        val progress = mutableListOf<CaptureProgress>()
        var tiles = emptyList<TileMetadata>()
        val job = launch { tiles = engine.startManualWeaving(onProgress = { progress.add(it) }) }
        advanceTimeBy(4_000); runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(LianyeState.CAPTURING, engine.state.value)
        assertEquals(listOf(1), progress.map { it.frameCount })
        assertEquals(1, capturer.cleanCalls)
        engine.stop(); job.join()
        assertEquals(height, tiles.sumOf { it.height })
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
        assertTrue(capturer.released)
    }

    @Test fun `a stationary large playing tile never asks the user to scroll back`() = runTest {
        fun video(color: Int): PixelSlice {
            val data = pattern(height).pixels.clone()
            for (y in 140 until 300) for (x in 0 until width) data[y * width + x] = color
            return PixelSlice(data, width, height)
        }
        val before = video(0xFF505050.toInt())
        val first = video(0xFF5A5A5A.toInt())
        val cut = video(0xFFCC2040.toInt())
        val capturer = Samples(listOf(before, first, cut, cut, cut), stabilizeFirst = false)
        val engine = engine(capturer)
        val unmatched = mutableListOf<Boolean>()
        val counts = mutableListOf<Int>()
        capturer.onSample = { if (it >= 5) engine.stop() }
        val tiles = engine.startManualWeaving(onProgress = { counts.add(it.frameCount) }, onUnmatched = { unmatched.add(it) })
        assertEquals(emptyList<Boolean>(), unmatched)
        assertEquals(listOf(1, 1), counts)
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
        assertEquals(pixels(first), pixels(tiles))
    }

    @Test fun `movement samples wait for stability and only one clean increment is counted`() = runTest {
        val base = pattern(height + 100)
        val capturer = Samples(listOf(slice(base, 0), slice(base, 20), slice(base, 40), slice(base, 80), slice(base, 80)))
        val engine = engine(capturer)
        val counts = mutableListOf<Int>()
        val tiles = engine.startManualWeaving(onProgress = {
            counts.add(it.frameCount)
            if (it.frameCount == 2) engine.stop()
        })
        assertEquals(height + 80, tiles.sumOf { it.height })
        assertEquals(listOf(1, 2, 2), counts)
        assertEquals(2, capturer.cleanCalls)
        assertEquals(6, capturer.sampleCalls)
    }

    @Test fun `a new buffer can settle without repeated display buffers`() = runTest {
        val base = pattern(height + 80)
        val capturer = Samples(listOf(slice(base, 0), slice(base, 80)))
        val engine = engine(capturer)
        val tiles = engine.startManualWeaving(onProgress = { if (it.frameCount == 2) engine.stop() })
        assertEquals(height + 80, tiles.sumOf { it.height })
        assertEquals(4, capturer.sampleCalls)
        assertEquals(2, capturer.cleanCalls)
    }

    @Test fun `reverse reports correction without duplicating content before further new content`() = runTest {
        val base = pattern(height + 200)
        val capturer = Samples(listOf(0, 80, 80, 40, 40, 80, 80, 160, 160).map { slice(base, it) })
        val engine = engine(capturer)
        val counts = mutableListOf<Int>()
        var reversals = 0
        val tiles = engine.startManualWeaving(onProgress = {
            counts.add(it.frameCount)
            if (it.frameCount == 3) engine.stop()
        }, onReverse = { reversals++ })
        assertEquals(height + 160, tiles.sumOf { it.height })
        assertEquals(listOf(1, 2, 3, 3), counts)
        assertEquals(1, reversals)
        assertEquals(pixels(slice(base, 0, height + 160)), pixels(tiles))
    }

    @Test fun `high resolution half screen displacement is supported beyond automatic search range`() = runTest {
        val viewport = 2400
        val shift = 1100
        val base = pattern(viewport + shift)
        val first = slice(base, 0, viewport)
        val next = slice(base, shift, viewport)
        val capturer = Samples(listOf(first, next, next))
        val engine = engine(capturer)
        val tiles = engine.startManualWeaving(onProgress = { if (it.frameCount == 2) engine.stop() })
        assertEquals(viewport + shift, tiles.sumOf { it.height })
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
    }

    @Test fun `default manual session continues sampling and recovers after a confirmed gap`() = runTest {
        val base = pattern(height + 80)
        val first = slice(base, 0)
        val unrelated = pattern(height, seed = 9811)
        val next = slice(base, 80)
        val capturer = Samples(listOf(first, unrelated, unrelated, unrelated, next, next))
        val engine = engine(capturer)
        val unmatched = mutableListOf<Boolean>()
        var tiles = emptyList<TileMetadata>()
        val job = launch { tiles = engine.startManualWeaving(
            onUnmatched = { unmatched.add(it) }, onProgress = { if (it.frameCount == 2) engine.stop() }) }
        advanceTimeBy(2_000); runCurrent()
        assertTrue("A transient gap must not freeze the session", job.isCompleted)
        assertEquals(listOf(true, false), unmatched)
        assertEquals(pixels(base), pixels(tiles))
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
    }

    @Test fun `first stable unmatched position reports failure even if user keeps changing position`() = runTest {
        val first = pattern(height)
        val gapA = pattern(height, seed = 9811)
        val gapB = pattern(height, seed = 7621)
        val capturer = Samples(listOf(first, first, gapA, gapA, gapB, gapB), stabilizeFirst = false)
        val engine = engine(capturer)
        val unmatched = mutableListOf<Boolean>()
        capturer.onSample = { index ->
            if (index == 4) assertEquals("The first stable failed join must already be visible", listOf(true), unmatched)
            if (index >= 6) engine.stop()
        }
        val tiles = engine.startManualWeaving(onUnmatched = { unmatched.add(it) })
        assertEquals(listOf(true), unmatched)
        assertEquals(pixels(first), pixels(tiles))
        assertEquals(CaptureCompletion.ALIGNMENT_FAILED, engine.completionReason)
    }

    @Test fun `raw controls never enter output pixels`() = runTest {
        val base = pattern(height + 80)
        val first = slice(base, 0)
        val next = slice(base, 80)
        fun withControl(frame: PixelSlice): PixelSlice {
            val data = frame.pixels.clone()
            for (y in 0 until 8) for (x in 0 until width) data[y * width + x] = 0xFFFF0000.toInt()
            return PixelSlice(data, width, height)
        }
        val capturer = Samples(listOf(withControl(first), withControl(next), withControl(next)))
        capturer.cleanFrames = listOf(first, next)
        val engine = engine(capturer)
        val tiles = engine.startManualWeaving(onProgress = { if (it.frameCount == 2) engine.stop() })
        assertEquals(pixels(base), pixels(tiles))
    }

    @Test fun `animation advancing during clean capture does not reject surrounding scroll evidence`() = runTest {
        val base = pattern(height + 80)
        val first = slice(base, 0)
        val next = slice(base, 80)
        val animatedPixels = next.pixels.clone()
        for (y in 200 until 212) for (x in 20 until 52) animatedPixels[y * width + x] = 0xFFE03020.toInt()
        val after = PixelSlice(animatedPixels, width, height)
        val capturer = Samples(listOf(first, next, next))
        capturer.cleanFrames = listOf(first, after)
        val engine = engine(capturer)
        val counts = mutableListOf<Int>()
        capturer.onSample = { if (it > 8) engine.stop() }
        val tiles = engine.startManualWeaving(onProgress = {
            counts.add(it.frameCount)
            if (it.frameCount == 2) engine.stop()
        })
        assertTrue("A late animation frame must not hide a proved scroll: $counts", counts.contains(2))
        assertEquals(pixels(base), pixels(tiles))
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
    }

    @Test fun `end does not sample or clean an extra frame`() = runTest {
        val base = pattern(height + 80)
        val first = slice(base, 0)
        val next = slice(base, 80)
        val capturer = Samples(listOf(first, next, next))
        val engine = engine(capturer)
        val tiles = engine.startManualWeaving(onProgress = { if (it.frameCount == 2) engine.stop() })
        assertEquals(height + 80, tiles.sumOf { it.height })
        assertEquals(4, capturer.sampleCalls)
        assertEquals(2, capturer.cleanCalls)
    }

    @Test fun `in flight clean read arriving after end is discarded`() = runTest {
        val base = pattern(height + 80)
        val capturer = Samples(listOf(slice(base, 0), slice(base, 80), slice(base, 80)))
        val engine = engine(capturer)
        capturer.onClean = { if (it == 1) engine.stop() }
        val tiles = engine.startManualWeaving()
        assertEquals(height, tiles.sumOf { it.height })
        assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
    }

    @Test fun `ending before first clean frame creates no image or progress`() = runTest {
        for (endDuringClean in listOf(false, true)) {
            val capturer = Samples(listOf(pattern(height)))
            val engine = engine(capturer)
            if (endDuringClean) capturer.onClean = { engine.stop() }
            else capturer.onSample = { engine.stop() }
            val progress = mutableListOf<CaptureProgress>()
            val tiles = engine.startManualWeaving(onProgress = { progress.add(it) })
            assertTrue(tiles.isEmpty())
            assertTrue(progress.isEmpty())
            assertEquals(if (endDuringClean) 1 else 0, capturer.cleanCalls)
            assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
            assertTrue(capturer.released)
        }
    }

    @Test fun `manual mode never executes or cancels accessibility gestures`() = runTest {
        var gestures = 0
        var cancellations = 0
        val dispatcher = object : GestureDispatcher {
            override fun sendGesture(command: ControlledScrollCommand, callback: (Boolean) -> Unit): Boolean {
                gestures++; callback(true); return true
            }
            override fun sendCancelGesture(): Boolean { cancellations++; return true }
        }
        val capturer = Samples(listOf(pattern(height)))
        val controller = AntiFlingController(dispatcher)
        val engine = engine(capturer, controller)
        engine.startManualWeaving(onProgress = { engine.stop() })
        val failedCapturer = Samples(listOf(pattern(height)))
        failedCapturer.onSample = { if (it > 1) error("Capture read failed") }
        assertEquals(height, engine(failedCapturer, controller).startManualWeaving().sumOf { it.height })
        assertEquals(0, gestures)
        assertEquals(0, cancellations)
    }

    @Test fun `first frame failure creates no output and releases session`() = runTest {
        var released = false
        val capturer = object : FrameCapturer {
            override suspend fun captureFrame(): PixelSlice? = null
            override fun releaseSession() { released = true }
        }
        val engine = engine(capturer)
        assertTrue(engine.startManualWeaving().isEmpty())
        assertEquals(CaptureCompletion.PERMISSION_LOST, engine.completionReason)
        assertTrue(released)
    }

    @Test fun `absence of first screen eventually permits a retry without inventing an image`() = runTest {
        val capturer = Samples(emptyList())
        val engine = engine(capturer)
        assertTrue(engine.startManualWeaving().isEmpty())
        assertEquals(CaptureCompletion.INTERRUPTED, engine.completionReason)
        assertEquals(0, capturer.cleanCalls)
        assertTrue(capturer.released)
    }

    @Test fun `secure first screen is rejected and later secure screen preserves valid prefix`() = runTest {
        val black = PixelSlice(IntArray(width * height) { 0xFF000000.toInt() }, width, height)
        val firstEngine = engine(Samples(listOf(black)))
        assertTrue(firstEngine.startManualWeaving().isEmpty())
        assertEquals(CaptureCompletion.SECURE_BLOCKED, firstEngine.completionReason)
        val later = engine(Samples(listOf(pattern(height), black, black)))
        assertEquals(height, later.startManualWeaving().sumOf { it.height })
        assertEquals(CaptureCompletion.SECURE_BLOCKED, later.completionReason)
    }

    @Test fun `storage interruption and coroutine cancellation preserve first screen`() = runTest {
        val capturer = Samples(listOf(pattern(height)))
        val storage = engine(capturer, reserve = Long.MAX_VALUE)
        assertEquals(height, storage.startManualWeaving().sumOf { it.height })
        assertEquals(CaptureCompletion.STORAGE_FULL, storage.completionReason)
        val store = TileStore(temp.newFolder(), chunkTargetHeight = 500)
        val cancelledCapturer = Samples(listOf(pattern(height)))
        val cancelled = LianyeEngine(OverlapMatcher(templateHeight = 100), null, store, cancelledCapturer)
        val job = launch { cancelled.startManualWeaving() }
        advanceTimeBy(250); runCurrent(); job.cancel(); job.join()
        assertEquals(height, store.committedTiles().sumOf { it.height })
        assertEquals(CaptureCompletion.INTERRUPTED, cancelled.completionReason)
        assertTrue(cancelledCapturer.released)
    }

    @Test fun `manual footer is retained once using the last accepted frame`() = runTest {
        val footerHeight = 60
        val shift = 80
        val base = pattern(height + shift * 2)
        fun frame(offset: Int): PixelSlice {
            val data = slice(base, offset).pixels
            for (y in height - footerHeight until height) for (x in 0 until width) {
                data[y * width + x] = if (x % 8 < 4) 0xFF20AA40.toInt() else 0xFF773388.toInt()
            }
            return PixelSlice(data, width, height)
        }
        val first = frame(0); val next = frame(shift); val last = frame(shift * 2)
        val engine = engine(Samples(listOf(first, next, next, last, last)))
        val tiles = engine.startManualWeaving(onProgress = { if (it.frameCount == 3) engine.stop() })
        assertEquals(height + shift * 2, tiles.sumOf { it.height })
        val data = pixels(tiles)
        val footerPixel = 0xFF20AA40.toInt()
        assertEquals(footerHeight * (width / 2), data.count { it == footerPixel })
    }

    @Test fun `translucent fixed footer does not repeat changing document pixels in each increment`() = runTest {
        val footerHeight = 80
        val shift = 80
        val base = pattern(height + shift * 2)
        fun frame(offset: Int): PixelSlice {
            val data = slice(base, offset).pixels
            for (y in height - footerHeight until height) for (x in 0 until width) {
                val level = 210 + (data[y * width + x] and 255) / 5
                data[y * width + x] = 0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
                if (y in height - footerHeight + 15 until height - 20 && x % 24 in 6..14 && y % 10 < 5)
                    data[y * width + x] = 0xFF404040.toInt()
            }
            return PixelSlice(data, width, height)
        }
        val last = frame(shift * 2)
        val engine = engine(Samples(listOf(frame(0), frame(shift), frame(shift), last, last)))
        val tiles = engine.startManualWeaving(onProgress = { if (it.frameCount == 3) engine.stop() })
        val expected = pixels(slice(base, 0, height - footerHeight + shift * 2)) + pixels(last.crop(height - footerHeight, footerHeight))
        assertEquals(expected, pixels(tiles))
    }

    @Test fun `first screen waits through moving raw samples before reporting real progress`() = runTest {
        val base = pattern(height + 80)
        val final = slice(base, 80)
        val capturer = Samples(listOf(slice(base, 0), slice(base, 20), slice(base, 40), final, final),
            stabilizeFirst = false)
        val engine = engine(capturer)
        val progress = mutableListOf<CaptureProgress>()
        val tiles = engine.startManualWeaving(onProgress = { progress.add(it); engine.stop() })
        assertEquals(pixels(final), pixels(tiles))
        assertEquals(5, capturer.sampleCalls)
        assertEquals(1, capturer.cleanCalls)
        assertTrue(progress.all { it.frameCount == 1 })
    }

    @Test fun `first clean position changing during acquisition requires stable confirmation again`() = runTest {
        val base = pattern(height + 80)
        val first = slice(base, 0)
        val settled = slice(base, 80)
        val capturer = Samples(listOf(first, first, settled, settled), stabilizeFirst = false)
        capturer.cleanFrames = listOf(settled, settled)
        val engine = engine(capturer)
        val tiles = engine.startManualWeaving(onProgress = { engine.stop() })
        assertEquals(pixels(settled), pixels(tiles))
        assertEquals(4, capturer.sampleCalls)
        assertEquals(2, capturer.cleanCalls)
    }

    @Test fun `notification obstruction can clear at the anchor or next reliable content without pollution`() = runTest {
        for (returnToAnchor in listOf(false, true)) {
            val base = pattern(height + 80)
            val first = slice(base, 0)
            val next = slice(base, 80)
            val unrelated = pattern(height, seed = 9811)
            val sequence = listOf(first, unrelated, unrelated, unrelated) +
                (if (returnToAnchor) listOf(first, first) else emptyList()) + listOf(next, next)
            val capturer = Samples(sequence)
            val engine = engine(capturer)
            val unmatched = mutableListOf<Boolean>()
            val tiles = engine.startManualWeaving(
                onProgress = { if (it.frameCount == 2) engine.stop() },
                onUnmatched = { unmatched.add(it) })
            assertEquals(listOf(true, false), unmatched)
            assertEquals(pixels(base), pixels(tiles))
            assertEquals(CaptureCompletion.USER_STOPPED, engine.completionReason)
        }
    }

    @Test fun `ending during notification obstruction marks only reliable prefix as not joined`() = runTest {
        val first = pattern(height)
        val unrelated = pattern(height, seed = 9811)
        val capturer = Samples(listOf(first, unrelated, unrelated, unrelated))
        val engine = engine(capturer)
        val unmatched = mutableListOf<Boolean>()
        val tiles = engine.startManualWeaving(onUnmatched = {
            unmatched.add(it)
            if (it) engine.stop()
        })
        assertEquals(listOf(true), unmatched)
        assertEquals(pixels(first), pixels(tiles))
        assertEquals(CaptureCompletion.ALIGNMENT_FAILED, engine.completionReason)
    }

    private fun pattern(h: Int, seed: Int = 913): PixelSlice {
        val data = IntArray(width * h)
        for (y in 0 until h) {
            val random = Random(seed + y * 7919)
            val row = random.nextInt(30, 225)
            for (x in 0 until width) {
                val value = (row + (x * 3 % 17)).coerceAtMost(255)
                data[y * width + x] = 0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
            }
        }
        return PixelSlice(data, width, h)
    }

    private fun slice(source: PixelSlice, start: Int, h: Int = height): PixelSlice =
        PixelSlice(IntArray(width * h).also { System.arraycopy(source.pixels, start * width, it, 0, it.size) }, width, h)

    private fun pixels(frame: PixelSlice): List<Int> = (0 until frame.height).flatMap { y ->
        (0 until frame.width).map { x -> frame.getPixel(x, y) }
    }

    private fun pixels(tiles: List<TileMetadata>): List<Int> = tiles.flatMap { tile ->
        val bytes = tile.file.readBytes()
        bytes.indices.step(4).map { offset ->
            ((bytes[offset].toInt() and 255) shl 24) or ((bytes[offset + 1].toInt() and 255) shl 16) or
                ((bytes[offset + 2].toInt() and 255) shl 8) or (bytes[offset + 3].toInt() and 255)
        }
    }
}
