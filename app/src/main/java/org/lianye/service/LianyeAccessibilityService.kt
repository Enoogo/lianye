package org.lianye.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import org.lianye.App
import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.CaptureMode
import org.lianye.domain.model.ManualPhase
import org.lianye.engine.*
import org.lianye.engine.model.LianyeState
import org.lianye.service.capture.FrameCapturerFactory
import org.lianye.platform.CaptureRegionStore
import org.lianye.service.gesture.AccessibilityGestureDispatcher
import org.lianye.service.gesture.AutoScrollCommandFactory
import org.lianye.service.gesture.AntiFlingController
import org.lianye.ui.floating.FloatingOverlayManager
import org.lianye.ui.floating.RegionSelectionOverlay
import org.lianye.ui.main.MainActivity
import java.lang.ref.WeakReference

class LianyeAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayManager: FloatingOverlayManager? = null
    private var lianyeEngine: LianyeEngine? = null
    private var frameCapturer: FrameCapturer? = null
    private var captureJob: Job? = null
    private var serviceBound = false
    private var regionOverlay: RegionSelectionOverlay? = null
    private val repository get() = App.instance.appComponent.lianyeRepository
    /** Set in onServiceConnected; reused by both the bubble and the Quick Settings tile. */
    private var beginAutoCapture: (() -> Unit)? = null

    /**
     * Collapses the Quick Settings / notification panel so the panel is not captured as part
     * of the long screenshot. Three-pronged approach for maximum reliability:
     *
     * 1. [GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE] (API 33+) — the canonical AccessibilityService
     *    action designed exactly for this purpose.
     * 2. [Intent.ACTION_CLOSE_SYSTEM_DIALOGS] broadcast — works on older devices where the
     *    global action is unavailable.
     * 3. A quick upward swipe gesture — simulates a human hand dismissing the panel, covering
     *    cases where both system APIs are ignored by OEM ROMs.
     *
     * Callers MUST wait for the collapse animation (~600ms) before capturing.
     */
    private fun collapseQuickSettings() {
        // 1. Global action (API 33+): instantly begins dismissing the shade / QS panel.
        runCatching {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        // 2. Broadcast fallback (mainly for API < 33).
        runCatching {
            val intent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(intent)
        }
        // 3. Swipe-up gesture: physically pushes the panel away like a human finger.
        runCatching { dispatchDismissSwipeGesture() }
    }

    /**
     * Dispatches a quick upward swipe from the lower-center of the screen to dismiss the
     * Quick Settings / notification panel. 200ms duration mimics a fast human flick.
     */
    private fun dispatchDismissSwipeGesture() {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val path = android.graphics.Path().apply {
            moveTo(cx, dm.heightPixels * 0.80f)
            lineTo(cx, dm.heightPixels * 0.25f)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
            path, 0L, 200L, false
        )
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceBound = true
        instance = WeakReference(this)
        repository.updateServiceConnected(true)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        var overlay: FloatingOverlayManager? = null
        val capturer = FrameCapturerFactory.create(this,
            { overlay?.hideBeforeCapture() ?: if (overlay == null) 0L else null },
            { withContext(Dispatchers.Main.immediate) { overlay?.showAfterCapture() } })
        frameCapturer = capturer
        val store = TileStore(App.instance.appComponent.draftStore.tilesDirectory, onCheckpoint = {
            repository.checkpointDraft(it)
            if (repository.lastPersistenceError.value != null) throw java.io.IOException("Draft checkpoint failed")
        })
        val engine = LianyeEngine(OverlapMatcher(screenDensity = resources.displayMetrics.density), AntiFlingController(AccessibilityGestureDispatcher(this)), store, capturer)
        lianyeEngine = engine
        beginAutoCapture = capture@{
            if (repository.captureMode.value != CaptureMode.AUTO || repository.manualSession.value.phase != ManualPhase.IDLE) return@capture
            if (repository.draft.value?.isSaved == false) openPreview()
            else if (captureJob?.isActive != true) {
                captureJob = serviceScope.launch(Dispatchers.IO) {
                    try {
                        if (repository.draft.value != null && !repository.discardDraft()) return@launch
                        if (!repository.startWeaving()) return@launch
                        val region = CaptureRegionStore.load(this@LianyeAccessibilityService)
                        val command = AutoScrollCommandFactory.build(
                            region,
                            overlay?.controlBounds,
                            resources.displayMetrics.density
                        )
                        val tiles = engine.startWeaving(
                            scrollCommand = command,
                            onProgress = { repository.updateProgress(it.frameCount, it.stitchedHeightPx, it.viewportHeightPx) }
                        )
                        if (engine.state.value == LianyeState.COMPLETED) repository.finishWeaving(tiles, engine.completionReason)
                        else repository.failWeaving(engine.failureMessage ?: "capture_interrupted", tiles, engine.completionReason)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        // A failed extension or tile write must end the session, not the app.
                        android.util.Log.e("LianyeCapture", "Capture session failed", error)
                        runCatching {
                            repository.failWeaving("capture_interrupted", emptyList(), CaptureCompletion.INTERRUPTED)
                        }
                    } finally {
                        // Same exit as the proven bubble flow: retire the floating control and
                        // hand the result to the preview.
                        withContext(Dispatchers.Main) {
                            overlay?.dismissOverlay()
                            openPreview()
                        }
                    }
                }
            }
        }
        overlay = FloatingOverlayManager(this, wm, repository, serviceScope,
            onStartCapture = { collapseQuickSettings(); beginAutoCapture?.invoke() },
            onStopCapture = {
                engine.stop()
                // Stop is an explicit "I am done": hide the floating control right away while
                // the already-captured rows are finalized in the background.
                overlay?.dismissOverlay()
            },
            onOpenPreview = { openPreview() }
        )
        overlayManager = overlay
        overlay.setPreviewVisible(previewVisible)
        serviceScope.launch {
            combine(repository.isProjectionGranted, repository.captureMode, repository.manualSession) { granted, mode, manual ->
                granted && mode == CaptureMode.AUTO && manual.phase == ManualPhase.IDLE
            }.collect { granted ->
                if (!granted && captureJob?.isActive == true) engine.stop(CaptureCompletion.PERMISSION_LOST)
                if (granted && getSharedPreferences("overlay", MODE_PRIVATE).getBoolean("enabled", true)) overlay.show()
                else overlay.hide()
            }
        }
    }

    fun openRegionEditor() {
        if (regionOverlay?.isVisible == true) return
        val editor = RegionSelectionOverlay(
            this,
            getSystemService(WINDOW_SERVICE) as WindowManager,
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        ) {
            // Confirmed or cancelled: hand the floating icon back so the next tap starts
            // a capture instead of reopening this editor.
            regionOverlay = null
            if (serviceBound) overlayManager?.show()
        }
        regionOverlay = editor
        editor.show()
    }

    private fun openPreview() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_draft", true)
        }
        try { startActivity(intent) } catch (_: RuntimeException) { /* Result remains available in the floating controls. */ }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        // Feedback interruption does not unbind the service or revoke permissions.
        lianyeEngine?.stop(CaptureCompletion.INTERRUPTED)
        overlayManager?.hide()
    }

    private fun releaseResources() {
        serviceBound = false
        lianyeEngine?.stop(CaptureCompletion.PERMISSION_LOST)
        captureJob?.cancel()
        repository.updateServiceConnected(false)
        overlayManager?.hide()
        regionOverlay?.hide()
        regionOverlay = null
        frameCapturer?.release()
        frameCapturer = null
        serviceScope.cancel()
        instance = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        releaseResources()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releaseResources()
        super.onDestroy()
    }

    companion object {
        private var instance: WeakReference<LianyeAccessibilityService>? = null
        private var previewVisible = false
        fun isConnected() = instance?.get()?.serviceBound == true
        fun showControls(): Boolean {
            val service = instance?.get() ?: return false
            if (service.repository.captureMode.value != CaptureMode.AUTO || service.repository.manualSession.value.phase != ManualPhase.IDLE) return false
            if (!service.repository.isProjectionGranted.value) return false
            service.getSharedPreferences("overlay", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            service.overlayManager?.show()
            return true
        }
        /** Opens the capture-region editor directly, without the intermediate bubble. */
        fun openRegionEditor(): Boolean {
            val service = instance?.get() ?: return false
            if (!service.serviceBound) return false
            service.overlayManager?.hide()
            service.openRegionEditor()
            return true
        }

        fun isOverlayVisible(): Boolean =
            instance?.get()?.repository?.isOverlayVisible?.value == true

        fun hideControls() {
            instance?.get()?.let { service ->
                service.getSharedPreferences("overlay", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
                service.overlayManager?.hide()
            }
        }
        fun setPreviewVisible(visible: Boolean) {
            previewVisible = visible
            instance?.get()?.overlayManager?.setPreviewVisible(visible)
        }
        /**
         * Quick Settings tile entry point: collapse the panel first so it is not stitched into
         * the long screenshot, wait for the dismiss animation to finish, then start an auto
         * capture directly (no bubble round-trip).
         *
         * The floating control is deliberately kept on screen: during the capture it shows the
         * Finish button (and the window is FLAG_SECURE, so it stays out of the stitched image).
         * Without it the user would have no way to stop a capture that never reaches the bottom.
         */
        fun startCaptureFromTile(): Boolean {
            val service = instance?.get() ?: return false
            if (!service.serviceBound) return false
            if (service.repository.captureMode.value != CaptureMode.AUTO ||
                service.repository.manualSession.value.phase != ManualPhase.IDLE
            ) return false
            if (!service.repository.isProjectionGranted.value) return false
            // Collapse Quick Settings / notification panel BEFORE the first frame is captured.
            service.collapseQuickSettings()
            // Show the control so the Finish button is reachable for the whole session.
            service.getSharedPreferences("overlay", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            service.overlayManager?.show()
            // Wait for the panel dismiss animation (~600ms) to complete before capturing,
            // otherwise the panel itself ends up in the first frame.
            service.serviceScope.launch {
                kotlinx.coroutines.delay(700L)
                service.beginAutoCapture?.invoke()
            }
            return true
        }
        fun disableCurrentService(): Boolean {
            val service = instance?.get() ?: return false
            service.lianyeEngine?.stop()
            return try { service.disableSelf(); true } catch (_: RuntimeException) { false }
        }
    }
}
