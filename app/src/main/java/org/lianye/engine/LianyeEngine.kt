package org.lianye.engine

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.CaptureProgress
import org.lianye.engine.model.LianyeState
import org.lianye.engine.model.PixelSlice
import org.lianye.engine.model.TileMetadata
import org.lianye.service.gesture.AntiFlingController
import org.lianye.service.gesture.model.ControlledScrollCommand
import java.io.IOException

class LianyeEngine(
    private val overlapMatcher: OverlapMatcher,
    private val antiFlingController: AntiFlingController?,
    private val tileStore: TileStore,
    private val frameCapturer: FrameCapturer,
    private val varianceMaskEngine: TemporalVarianceMask = TemporalVarianceMask(),
    val screenDensity: Float = overlapMatcher.screenDensity,
    private val minimumFreeStorageBytes: Long = 64L * 1024 * 1024
) {
    private val _state = MutableStateFlow(LianyeState.IDLE)
    val state: StateFlow<LianyeState> = _state.asStateFlow()

    private val weaveMutex = Mutex()
    private val frameWindow = mutableListOf<PixelSlice>()
    private var priorDeltaY: Int? = null

    @Volatile
    private var isStopRequested = false
    @Volatile
    private var requestedStopReason = CaptureCompletion.USER_STOPPED

    var completionReason: CaptureCompletion = CaptureCompletion.COMPLETED
        private set
    var failureMessage: String? = null
        private set

    suspend fun startWeaving(
        maxFrames: Int = Int.MAX_VALUE,
        scrollCommand: ControlledScrollCommand? = null,
        onProgress: (CaptureProgress) -> Unit = {}
    ): List<TileMetadata> {
        require(maxFrames > 0) { "maxFrames must be positive" }
        if (!weaveMutex.tryLock()) {
            return emptyList()
        }

        var initialFrame: PixelSlice? = null
        var prevSlice: PixelSlice? = null
        var isInitialFrameWritten = false
        var bottomBarHeight = 0
        var frameCount = 0
        fun reportProgress() {
            val initial = initialFrame ?: return
            val height = if (isInitialFrameWritten) tileStore.currentHeightPx + bottomBarHeight else initial.height
            onProgress(CaptureProgress(frameCount, height, initial.height))
        }
        fun finalizeContent(): List<TileMetadata> {
            if (!isInitialFrameWritten) {
                isInitialFrameWritten = initialFrame != null
                initialFrame?.let { tileStore.appendStrip(it) }
            } else if (bottomBarHeight > 0) {
                val last = prevSlice ?: initialFrame
                val footerHeight = bottomBarHeight
                bottomBarHeight = 0
                if (last != null) tileStore.appendStrip(last.crop(last.height - footerHeight, footerHeight))
            }
            return tileStore.finalizeTiles()
        }

        try {
            isStopRequested = false
            requestedStopReason = CaptureCompletion.USER_STOPPED
            completionReason = CaptureCompletion.COMPLETED
            failureMessage = null
            org.lianye.platform.LianyeLogStore.captureEvent("startWeaving", "maxFrames=$maxFrames scrollCommand=$scrollCommand")
            frameCapturer.setCaptureSpan(CaptureSpan.BAND)
            frameCapturer.prepareSession()
            tileStore.clear()
            frameWindow.clear()
            priorDeltaY = null
            _state.value = LianyeState.CAPTURING

            // Full-height mode: the very first frame already contains everything above the
            // band, so no second screenshot is needed (and no screenshot-rate limiting).
            val fullHeight = frameCapturer.fullHeightMode
            if (fullHeight) frameCapturer.setCaptureSpan(CaptureSpan.FIRST)

            var hasStitchedAny = false
            var consecutiveStationaryCount = 0
            var consecutiveDynamicCount = 0
            var lastMatchSad = 0.0f

            val captured0 = frameCapturer.captureFrame() ?: run {
                Log.e(TAG, "captureFrame failed for initial frame")
                completionReason = frameCapturer.failureReason
                failureMessage = completionReason.name.lowercase()
                _state.value = LianyeState.ERROR
                return emptyList()
            }
            Log.i(TAG, "Initial frame captured: ${captured0.width}x${captured0.height}")
            val topExtra = if (fullHeight) {
                frameCapturer.contentTopInScreen.coerceIn(0, (captured0.height - 1).coerceAtLeast(0))
            } else 0
            val matchFrame = if (topExtra > 0) captured0.crop(topExtra, captured0.height - topExtra) else captured0
            initialFrame = captured0
            frameWindow.add(matchFrame)
            prevSlice = matchFrame
            frameCount = 1
            reportProgress()
            if (fullHeight) frameCapturer.setCaptureSpan(CaptureSpan.BAND)

            // Condition 1: Adaptive gesture coordinate mapping with double-clamped stroke
            val effectiveCommand = scrollCommand ?: run {
                val w = matchFrame.width.toFloat()
                val h = matchFrame.height.toFloat()
                val strokeDist = minOf(h * 0.35f, 280.0f * screenDensity)
                val centerX = w * 0.50f
                // Frames are cropped to the user's region, but injected gestures use screen
                // coordinates: shift the stroke into the selected band.
                val centerY = frameCapturer.contentTopInScreen + h * 0.50f
                ControlledScrollCommand(
                    startX = centerX,
                    startY = centerY + strokeDist / 2.0f,
                    endX = centerX,
                    endY = centerY - strokeDist / 2.0f,
                    moveDurationMs = 550L,
                    holdDurationMs = 150L
                )
            }

            for (frameIndex in 1 until maxFrames) {
                if (isStopRequested) {
                    Log.i(TAG, "Stop requested at frame $frameIndex")
                    completionReason = requestedStopReason
                    if (requestedStopReason == CaptureCompletion.USER_STOPPED) {
                        _state.value = LianyeState.STOPPING
                    } else {
                        failureMessage = requestedStopReason.name.lowercase()
                        _state.value = LianyeState.ERROR
                    }
                    break
                }
                if (tileStore.availableStorageBytes < minimumFreeStorageBytes) {
                    completionReason = CaptureCompletion.STORAGE_FULL
                    failureMessage = "storage_full"
                    _state.value = LianyeState.ERROR
                    break
                }
                val currentPrev = prevSlice ?: break
                Log.d(TAG, "Processing frame $frameIndex...")
                when (val step = processFrameStep(
                    prevSlice = currentPrev,
                    command = effectiveCommand,
                    frameIndex = frameIndex,
                    hasStitchedAny = hasStitchedAny,
                    initialFrame = initialFrame,
                    isInitialFrameWritten = isInitialFrameWritten,
                    bottomBarHeight = bottomBarHeight,
                    consecutiveStationaryCount = consecutiveStationaryCount,
                    consecutiveDynamicCount = consecutiveDynamicCount,
                    onInitialFrameWritten = { detectedHeight ->
                        isInitialFrameWritten = true
                        bottomBarHeight = detectedHeight
                    },
                    onCountersUpdated = { statCount, dynCount, lastSad ->
                        consecutiveStationaryCount = statCount
                        consecutiveDynamicCount = dynCount
                        lastMatchSad = lastSad
                    }
                )) {
                    is FrameStepResult.Continue -> {
                        prevSlice = step.nextSlice
                        hasStitchedAny = true
                        frameCount++
                    }
                    is FrameStepResult.Completed -> {
                        if (step.finalSlice != null) {
                            prevSlice = step.finalSlice
                        }
                        hasStitchedAny = true
                        if (step.finalSlice != null) frameCount++
                        completionReason = if (isStopRequested) requestedStopReason else CaptureCompletion.COMPLETED
                        _state.value = if (isStopRequested && requestedStopReason != CaptureCompletion.USER_STOPPED) LianyeState.ERROR else LianyeState.COMPLETED
                        if (_state.value == LianyeState.ERROR) failureMessage = completionReason.name.lowercase()
                        break
                    }
                    is FrameStepResult.Error -> {
                        completionReason = step.reason
                        failureMessage = step.reason.name.lowercase()
                        _state.value = LianyeState.ERROR
                        break
                    }
                    is FrameStepResult.Stop -> {
                        completionReason = step.reason
                        failureMessage = step.reason.name.lowercase()
                        _state.value = LianyeState.ERROR
                        break
                    }
                }
                reportProgress()
            }

            if (_state.value != LianyeState.ERROR && _state.value != LianyeState.COMPLETED) {
                // Explicit caller caps are safety interruptions, not a claim that the bottom was reached.
                completionReason = if (isStopRequested) requestedStopReason else CaptureCompletion.INTERRUPTED
                _state.value = if (isStopRequested && requestedStopReason != CaptureCompletion.USER_STOPPED) LianyeState.ERROR else LianyeState.COMPLETED
                if (_state.value == LianyeState.ERROR) failureMessage = completionReason.name.lowercase()
            }

            val bottomStrip = if (fullHeight) captureBottomStrip() else null
            var tiles = finalizeContent()
            if (bottomStrip != null) {
                tileStore.appendStrip(bottomStrip)
                tiles = tileStore.finalizeTiles()
            }
            Log.i(TAG, "Full-height=$fullHeight tiles=${tiles.joinToString { it.height.toString() }}")
            onProgress(CaptureProgress(frameCount, tiles.sumOf { it.height }, captured0.height))
            Log.i(TAG, "Weaving finalized: state=${_state.value}, hasStitchedAny=$hasStitchedAny, tileCount=${tiles.size}")
            org.lianye.platform.LianyeLogStore.completionWithSad(completionReason, frameCount, tiles.sumOf { it.height }, lastMatchSad)
            return tiles
        } catch (e: Exception) {
            Log.e(TAG, "Exception during weaving", e)
            _state.value = LianyeState.ERROR
            runCatching { antiFlingController?.cancelCurrentGesture() }
            completionReason = when (e) {
                is IOException -> CaptureCompletion.STORAGE_FULL
                is CancellationException -> if (isStopRequested) requestedStopReason else CaptureCompletion.INTERRUPTED
                else -> CaptureCompletion.INTERRUPTED
            }
            failureMessage = completionReason.name.lowercase()
            if (completionReason == CaptureCompletion.USER_STOPPED) _state.value = LianyeState.COMPLETED
            // A cancelled service must still close and commit valid image rows before releasing capture.
            return withContext(NonCancellable) {
                runCatching { finalizeContent() }.getOrElse { tileStore.salvageTiles() }
            }
        } finally {
            runCatching { frameCapturer.releaseSession() }
            frameWindow.clear()
            weaveMutex.unlock()
        }
    }

    /**
     * Manual capture waits for stable user-scrolled content. Only clean, accepted frames
     * enter the tile store; polling and an unchanged display never count as another screen.
     */
    suspend fun startManualWeaving(
        maxFrames: Int = Int.MAX_VALUE,
        onProgress: (CaptureProgress) -> Unit = {},
        onUnmatched: (Boolean) -> Unit = {},
        onReverse: () -> Unit = {}
    ): List<TileMetadata> {
        require(maxFrames > 0) { "maxFrames must be positive" }
        if (!weaveMutex.tryLock()) return emptyList()

        val analyzer = ManualFrameAnalyzer(overlapMatcher)
        var initialFrame: PixelSlice? = null
        var anchor: PixelSlice? = null
        var anchorRaw: PixelSlice? = null
        var anchorChanges: BooleanArray? = null
        var initialWritten = false
        var bottomBarHeight = 0
        var frameCount = 0
        var viewportHeight = 0
        var unmatchedActive = false
        fun mergeChanges(existing: BooleanArray?, observed: BooleanArray): BooleanArray {
            if (existing != null) for (index in observed.indices)
                observed[index] = observed[index] || existing.getOrElse(index) { false }
            return observed
        }
        fun reportProgress() {
            val height = if (initialWritten) tileStore.currentHeightPx + bottomBarHeight else viewportHeight
            onProgress(CaptureProgress(frameCount, height, viewportHeight))
        }
        fun finalizeContent(): List<TileMetadata> {
            if (!initialWritten) {
                initialFrame?.let {
                    initialWritten = true
                    tileStore.appendStrip(it)
                }
            } else if (bottomBarHeight > 0) {
                val footer = bottomBarHeight
                bottomBarHeight = 0
                anchor?.let { tileStore.appendStrip(it.crop(it.height - footer, footer)) }
            }
            return tileStore.finalizeTiles()
        }
        fun fail(reason: CaptureCompletion) {
            completionReason = reason
            failureMessage = reason.name.lowercase()
            _state.value = LianyeState.ERROR
        }

        try {
            isStopRequested = false
            requestedStopReason = CaptureCompletion.USER_STOPPED
            completionReason = CaptureCompletion.COMPLETED
            failureMessage = null
            frameCapturer.setCaptureSpan(CaptureSpan.BAND)
            frameCapturer.prepareSession()
            tileStore.clear()
            _state.value = LianyeState.CAPTURING

            val fullHeight = frameCapturer.fullHeightMode
            if (fullHeight) {
                runCatching {
                    frameCapturer.setCaptureSpan(CaptureSpan.TOP_ONLY)
                    frameCapturer.captureFrame()?.takeIf { it.height > 0 }?.let { tileStore.appendStrip(it) }
                }.onFailure { Log.w(TAG, "Top extension unavailable", it) }
                frameCapturer.setCaptureSpan(CaptureSpan.BAND)
                // The manual sampler polls quickly; let the screenshot API recover.
                delay(400L)
            }

            var handledRaw: PixelSlice? = null
            var firstAttempts = 0
            var initialRaw: PixelSlice? = null
            var initialStableSamples = 0
            while (anchor == null && !isStopRequested) {
                if (firstAttempts++ >= FIRST_FRAME_ATTEMPTS) {
                    fail(CaptureCompletion.INTERRUPTED)
                    return emptyList()
                }
                val raw = when (val sample = frameCapturer.sampleFrame()) {
                    FrameSample.NoNewFrame -> initialRaw
                    is FrameSample.Failed -> {
                        fail(sample.reason)
                        return emptyList()
                    }
                    is FrameSample.Available -> sample.frame
                }
                if (isStopRequested) break
                if (raw != null) {
                    val priorInitialRaw = initialRaw
                    initialStableSamples = if (priorInitialRaw?.let { analyzer.samePosition(it, raw) } == true) {
                        initialStableSamples + 1
                    } else 1
                    initialRaw = raw
                    if (initialStableSamples >= STABLE_SAMPLE_COUNT) {
                        when (val clean = frameCapturer.captureCleanFrame(raw)) {
                            FrameSample.NoNewFrame -> Unit
                            is FrameSample.Failed -> {
                                fail(clean.reason)
                                return emptyList()
                            }
                            is FrameSample.Available -> {
                                if (isStopRequested) break
                                if (clean.frame.height <= 0) {
                                    fail(CaptureCompletion.ALIGNMENT_FAILED)
                                    return emptyList()
                                }
                                if (analyzer.isSecureBlocked(clean.frame)) {
                                    fail(CaptureCompletion.SECURE_BLOCKED)
                                    return emptyList()
                                }
                                if (analyzer.samePosition(raw, clean.frame)) {
                                    initialFrame = clean.frame
                                    anchor = clean.frame
                                    anchorRaw = raw
                                    anchorChanges = mergeChanges(priorInitialRaw?.let { analyzer.changedRegions(it, raw) },
                                        analyzer.changedRegions(raw, clean.frame))
                                    handledRaw = raw
                                    viewportHeight = clean.frame.height
                                    frameCount = 1
                                    reportProgress()
                                } else initialStableSamples = 0
                            }
                        }
                    }
                }
                if (anchor == null && !isStopRequested) delay(MANUAL_SAMPLE_INTERVAL_MS)
            }

            var previousRaw: PixelSlice? = handledRaw
            var stableSamples = 1
            while (anchor != null && frameCount < maxFrames && !isStopRequested) {
                delay(MANUAL_SAMPLE_INTERVAL_MS)
                if (isStopRequested) break
                if (tileStore.availableStorageBytes < minimumFreeStorageBytes) {
                    fail(CaptureCompletion.STORAGE_FULL)
                    break
                }
                val raw = when (val sample = frameCapturer.sampleFrame()) {
                    // ImageReader need not publish duplicate buffers on an idle display.
                    // A previously changed sample can settle during a quiet interval;
                    // the clean confirmation below still verifies its position.
                    FrameSample.NoNewFrame -> previousRaw ?: continue
                    is FrameSample.Failed -> {
                        fail(sample.reason)
                        break
                    }
                    is FrameSample.Available -> sample.frame
                }
                if (isStopRequested) break
                val priorRaw = previousRaw
                stableSamples = if (priorRaw?.let { analyzer.samePosition(it, raw) } == true) {
                    stableSamples + 1
                } else 1
                previousRaw = raw
                val repeatsHandledFrame = handledRaw?.let { analyzer.samePosition(it, raw) } == true
                if (repeatsHandledFrame && !unmatchedActive) {
                    anchorRaw?.takeIf { analyzer.samePosition(it, raw) }?.let { original ->
                        val changes = analyzer.changedRegions(original, raw)
                        val existing = anchorChanges
                        if (existing != null) for (index in changes.indices) changes[index] = changes[index] || existing[index]
                        anchorChanges = changes
                    }
                    continue
                }
                if (stableSamples < STABLE_SAMPLE_COUNT || (unmatchedActive && handledRaw == raw)) continue
                val sampledChanges = priorRaw?.let { analyzer.changedRegions(it, raw) }

                val clean = when (val sample = frameCapturer.captureCleanFrame(raw)) {
                    FrameSample.NoNewFrame -> continue
                    is FrameSample.Failed -> {
                        fail(sample.reason)
                        break
                    }
                    is FrameSample.Available -> sample.frame
                }
                // End never initiates a new screenshot. An in-flight read after End is also
                // discarded, since a notification panel or return transition may now be visible.
                if (isStopRequested) break
                if (!analyzer.samePosition(raw, clean)) {
                    stableSamples = 0
                    continue
                }
                // Playing tiles can change during the transparent-window fence/read.
                // These two frames have a confirmed identical layout, so include that
                // observed temporal change rather than treating it as a wrong seam.
                val candidateChanges = mergeChanges(sampledChanges, analyzer.changedRegions(raw, clean))
                val currentAnchor = anchor ?: break
                _state.value = LianyeState.WEAVING
                val matchStarted = System.nanoTime()
                val motion = analyzer.analyze(currentAnchor, clean, anchorChanges, candidateChanges)
                Log.d(TAG, "Manual match: $motion, ${analyzer.lastDecisionDetails}, ${(System.nanoTime() - matchStarted) / 1_000_000} ms")
                when (motion) {
                    ManualFrameAnalyzer.Motion.Stationary -> {
                        handledRaw = raw
                        if (unmatchedActive) {
                            unmatchedActive = false
                            onUnmatched(false)
                        }
                    }
                    ManualFrameAnalyzer.Motion.Reverse -> {
                        handledRaw = raw
                        if (unmatchedActive) {
                            unmatchedActive = false
                            onUnmatched(false)
                        }
                        // The accepted anchor stays unchanged. Re-show forward instructions
                        // even for users whose ordinary guide has become a compact route.
                        onReverse()
                    }
                    is ManualFrameAnalyzer.Motion.Forward -> {
                        if (!initialWritten) {
                            val first = initialFrame ?: currentAnchor
                            // A translucent pinned bar has changing background pixels, so
                            // same-position color alone cannot identify its whole extent.
                            val unmatchedEdge = (first.height - analyzer.scrollingBottom)
                                .coerceIn(0, minOf(first.height / 4, 600))
                            bottomBarHeight = maxOf(detectBottomBarHeight(first, clean, motion.deltaY), unmatchedEdge)
                            initialWritten = true
                            tileStore.appendStrip(first.crop(0, first.height - bottomBarHeight))
                            initialFrame = null
                        }
                        appendIncrementStrip(clean, motion.deltaY, bottomBarHeight)
                        anchor = clean
                        anchorRaw = raw
                        anchorChanges = candidateChanges
                        handledRaw = raw
                        if (unmatchedActive) {
                            unmatchedActive = false
                            onUnmatched(false)
                        }
                        frameCount++
                        reportProgress()
                    }
                    ManualFrameAnalyzer.Motion.SecureBlocked -> {
                        fail(CaptureCompletion.SECURE_BLOCKED)
                        break
                    }
                    ManualFrameAnalyzer.Motion.Unmatched -> {
                        // This candidate already settled across samples and was confirmed
                        // at the same position by a clean read. Waiting for another identical
                        // failure silences feedback whenever the user or a video keeps moving.
                        if (!unmatchedActive) {
                            unmatchedActive = true
                            onUnmatched(true)
                        }
                        handledRaw = raw
                    }
                }
                _state.value = LianyeState.CAPTURING
            }

            if (_state.value != LianyeState.ERROR) {
                completionReason = when {
                    isStopRequested && requestedStopReason != CaptureCompletion.USER_STOPPED -> requestedStopReason
                    unmatchedActive -> CaptureCompletion.ALIGNMENT_FAILED
                    isStopRequested -> requestedStopReason
                    else -> CaptureCompletion.INTERRUPTED
                }
                _state.value = if (completionReason == CaptureCompletion.USER_STOPPED || completionReason == CaptureCompletion.INTERRUPTED) {
                    LianyeState.COMPLETED
                } else LianyeState.ERROR
                if (_state.value == LianyeState.ERROR) failureMessage = completionReason.name.lowercase()
            }
            val bottomStrip = if (fullHeight) captureBottomStrip() else null
            var tiles = finalizeContent()
            if (bottomStrip != null) {
                tileStore.appendStrip(bottomStrip)
                tiles = tileStore.finalizeTiles()
            }
            if (frameCount > 0) onProgress(CaptureProgress(frameCount, tiles.sumOf { it.height }, viewportHeight))
            return tiles
        } catch (exception: Exception) {
            Log.e(TAG, "Exception during manual capture", exception)
            completionReason = when (exception) {
                is IOException -> CaptureCompletion.STORAGE_FULL
                is CancellationException -> if (isStopRequested) requestedStopReason else CaptureCompletion.INTERRUPTED
                else -> CaptureCompletion.INTERRUPTED
            }
            failureMessage = completionReason.name.lowercase()
            _state.value = if (completionReason == CaptureCompletion.USER_STOPPED) LianyeState.COMPLETED else LianyeState.ERROR
            return withContext(NonCancellable) {
                if (exception is IOException) tileStore.salvageTiles()
                else runCatching { finalizeContent() }.getOrElse { tileStore.salvageTiles() }
            }
        } finally {
            runCatching { frameCapturer.releaseSession() }
            weaveMutex.unlock()
        }
    }

    /**
     * Grabs what sits below the band while the capture session is still alive.
     * Failures are non-fatal: a missing strip is better than losing the whole capture.
     */
    private suspend fun captureBottomStrip(): PixelSlice? {
        val strip = runCatching {
            frameCapturer.setCaptureSpan(CaptureSpan.BOTTOM_ONLY)
            frameCapturer.captureFrame()
        }.onFailure { Log.w(TAG, "Bottom extension unavailable", it) }.getOrNull()
        Log.i(TAG, "Bottom extension height=${strip?.height ?: -1}")
        frameCapturer.setCaptureSpan(CaptureSpan.BAND)
        return strip?.takeIf { it.height > 0 }
    }

    fun stop(reason: CaptureCompletion = CaptureCompletion.USER_STOPPED) {
        requestedStopReason = reason
        isStopRequested = true
    }

    fun detectBottomBarHeight(
        frame0: PixelSlice,
        frame1: PixelSlice,
        deltaY: Int
    ): Int {
        if (deltaY <= 0) return 0
        val w = minOf(frame0.width, frame1.width)
        val h = minOf(frame0.height, frame1.height)
        val maxFooterH = minOf(h / 4, 600)
        val startX = (w * 0.1f).toInt()
        val endX = (w * 0.9f).toInt()

        var detectedFooterH = 0
        var consecutiveMotionRows = 0

        for (offset in 0 until maxFooterH) {
            val y = h - 1 - offset
            val yShifted = y - deltaY
            if (yShifted < 0) break

            var staticDiffSum = 0L
            var motionDiffSum = 0L
            var samples = 0

            for (x in startX until endX step 4) {
                val p0 = frame0.getPixel(x, y)
                val p1 = frame1.getPixel(x, y)
                val p1Shifted = frame1.getPixel(x, yShifted)

                val l0 = ((77 * ((p0 ushr 16) and 0xFF) + 150 * ((p0 ushr 8) and 0xFF) + 29 * (p0 and 0xFF)) ushr 8)
                val l1 = ((77 * ((p1 ushr 16) and 0xFF) + 150 * ((p1 ushr 8) and 0xFF) + 29 * (p1 and 0xFF)) ushr 8)
                val l1s = ((77 * ((p1Shifted ushr 16) and 0xFF) + 150 * ((p1Shifted ushr 8) and 0xFF) + 29 * (p1Shifted and 0xFF)) ushr 8)

                staticDiffSum += kotlin.math.abs(l0 - l1)
                motionDiffSum += kotlin.math.abs(l0 - l1s)
                samples++
            }

            val rStatic = staticDiffSum.toFloat() / maxOf(1, samples)
            val rMotion = motionDiffSum.toFloat() / maxOf(1, samples)

            val isStaticRow = (rStatic <= 8.0f && rMotion > 12.0f)
            if (isStaticRow) {
                detectedFooterH = offset + 1
                consecutiveMotionRows = 0
            } else if (rMotion < rStatic && rMotion <= 10.0f) {
                consecutiveMotionRows++
                if (consecutiveMotionRows >= 12) {
                    break
                }
            }
        }

        val minBottomBarPx = maxOf(20, (24.0f * screenDensity).toInt())
        return if (detectedFooterH >= minBottomBarPx) detectedFooterH else 0
    }

    private suspend fun processFrameStep(
        prevSlice: PixelSlice,
        command: ControlledScrollCommand,
        frameIndex: Int,
        hasStitchedAny: Boolean,
        initialFrame: PixelSlice?,
        isInitialFrameWritten: Boolean,
        bottomBarHeight: Int,
        consecutiveStationaryCount: Int,
        consecutiveDynamicCount: Int,
        onInitialFrameWritten: (Int) -> Unit,
        onCountersUpdated: (Int, Int, Float) -> Unit
    ): FrameStepResult {
        if (antiFlingController?.executeControlledScroll(command) != true) {
            Log.e(TAG, "executeControlledScroll returned false at frame $frameIndex")
            org.lianye.platform.LianyeLogStore.log("E", TAG, "frame=$frameIndex scroll gesture FAILED -> stop")
            return FrameStepResult.Stop(CaptureCompletion.PERMISSION_LOST)
        }

        // Settling delay: allow target app VSYNC layout, redraw and overscroll spring bounce to settle
        delay(350L)
        if (isStopRequested) return if (requestedStopReason == CaptureCompletion.USER_STOPPED) {
            FrameStepResult.Completed()
        } else {
            FrameStepResult.Stop(requestedStopReason)
        }

        _state.value = LianyeState.CAPTURING
        val nextSlice = frameCapturer.captureFrame() ?: run {
            Log.e(TAG, "captureFrame returned null at frame $frameIndex")
            return FrameStepResult.Stop(frameCapturer.failureReason)
        }

        _state.value = LianyeState.WEAVING
        frameWindow.add(nextSlice)
        if (frameWindow.size > 3) {
            frameWindow.removeAt(0)
        }

        val rowMask = if (frameWindow.size == 3) {
            varianceMaskEngine.computeRowMask(frameWindow[0], frameWindow[1], frameWindow[2])
        } else {
            null
        }

        // The injected stroke travels a known distance; a controlled drag moves the page by
        // roughly that much. Hand it to the matcher so periodic-list alias peaks cannot win
        // on noise (field: 653px strokes answered 116px/44px → content silently dropped).
        val expectedDeltaY = kotlin.math.round(command.startY - command.endY).toInt().coerceAtLeast(0)
        val matchResult = overlapMatcher.match(
            prevSlice = prevSlice,
            nextSlice = nextSlice,
            rowMask = rowMask,
            priorDeltaY = priorDeltaY,
            expectedDeltaY = expectedDeltaY
        )

        Log.d(TAG, "Frame $frameIndex matchResult: deltaY=${matchResult.deltaY}, isBottomReached=${matchResult.isBottomReached}, isStationary=${matchResult.isStationary}, isDynamicScene=${matchResult.isDynamicScene}, isSecureBlocked=${matchResult.isSecureBlocked}, sadScore=${matchResult.sadScore}, ambiguityRatio=${matchResult.ambiguityRatio}")

        if (matchResult.deltaY > 0) {
            priorDeltaY = matchResult.deltaY
        }

        if (matchResult.isSecureBlocked) return FrameStepResult.Error(CaptureCompletion.SECURE_BLOCKED)

        val newStatCount = if (matchResult.isStationary) consecutiveStationaryCount + 1 else 0
        val newDynCount = if (matchResult.isDynamicScene) consecutiveDynamicCount + 1 else 0
        onCountersUpdated(newStatCount, newDynCount, matchResult.sadScore)

        if (matchResult.isDynamicScene) return FrameStepResult.Error(CaptureCompletion.ALIGNMENT_FAILED)

        var currentBottomBarH = bottomBarHeight
        if (matchResult.deltaY > 0 && !isInitialFrameWritten && initialFrame != null) {
            currentBottomBarH = detectBottomBarHeight(prevSlice, nextSlice, matchResult.deltaY)
            val bodyHeight = initialFrame.height - currentBottomBarH
            onInitialFrameWritten(currentBottomBarH)
            if (bodyHeight > 0) {
                tileStore.appendStrip(initialFrame.crop(0, bodyHeight))
            }
            Log.i(TAG, "Initial frame written with bodyHeight=$bodyHeight, bottomBarHeight=$currentBottomBarH")
        }

        // A deformed match (stationary but high residual) must NOT count toward the bottom
        // decision: it means the capture-timing alignment failed for this frame, not that the
        // page stopped. Reset the consecutive-stationary counter so the engine retries the
        // scroll instead of terminating at frame 1.
        val isDeformed = matchResult.isDeformedMatch
        val effStatCount = if (isDeformed) 0 else newStatCount

        // Bottom reached requires either a truly clean stationary match (SAD <= 4.0) OR
        // two consecutive clean stationary frames. A single deformed stationary frame is
        // neither and must not terminate the capture.
        val isBottom = matchResult.isBottomReached || effStatCount >= 2
        org.lianye.platform.LianyeLogStore.log(
            "D", TAG,
            "frame=$frameIndex decision isBottom=$isBottom (bottom=${matchResult.isBottomReached}, stat=$effStatCount/${consecutiveStationaryCount}+1" +
                (if (isDeformed) " deformed" else "") + ") " +
                "deltaY=${matchResult.deltaY} expected=$expectedDeltaY sad=${matchResult.sadScore} initialWritten=$isInitialFrameWritten hasStitched=$hasStitchedAny"
        )

        if (isBottom) {
            if (matchResult.deltaY > 0) {
                appendIncrementStrip(nextSlice, matchResult.deltaY, currentBottomBarH)
                return FrameStepResult.Completed(nextSlice)
            } else if (hasStitchedAny) {
                return FrameStepResult.Completed(nextSlice)
            } else {
                // First frame already stationary: only accept a clean-stop residual. A
                // high residual here is a capture-timing artifact and must not commit a
                // 1-frame "long screenshot".
                return if (matchResult.sadScore <= 8.0f) {
                    FrameStepResult.Completed(nextSlice)
                } else {
                    Log.e(TAG, "Frame $frameIndex failed match with sadScore=${matchResult.sadScore} on initial step")
                    FrameStepResult.Error(CaptureCompletion.ALIGNMENT_FAILED)
                }
            }
        }

        appendIncrementStrip(nextSlice, matchResult.deltaY, currentBottomBarH)
        return FrameStepResult.Continue(nextSlice)
    }

    private fun appendIncrementStrip(nextSlice: PixelSlice, deltaY: Int, bottomBarHeight: Int) {
        if (deltaY > 0) {
            val safeCropStartY = maxOf(0, (nextSlice.height - bottomBarHeight) - deltaY)
            val safeCropHeight = minOf(deltaY, nextSlice.height - safeCropStartY)
            if (safeCropHeight > 0) {
                val strip = nextSlice.crop(
                    startY = safeCropStartY,
                    cropHeight = safeCropHeight
                )
                tileStore.appendStrip(strip)
            }
        }
    }

    private sealed interface FrameStepResult {
        data class Continue(val nextSlice: PixelSlice) : FrameStepResult
        data class Completed(val finalSlice: PixelSlice? = null) : FrameStepResult
        data class Error(val reason: CaptureCompletion) : FrameStepResult
        data class Stop(val reason: CaptureCompletion) : FrameStepResult
    }

    companion object {
        private const val TAG = "LianyeWeaving"
        private const val MANUAL_SAMPLE_INTERVAL_MS = 250L
        private const val STABLE_SAMPLE_COUNT = 2
        private const val FIRST_FRAME_ATTEMPTS = 8
    }
}
