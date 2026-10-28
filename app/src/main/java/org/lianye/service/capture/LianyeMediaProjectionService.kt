package org.lianye.service.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.lianye.App
import org.lianye.R
import org.lianye.domain.model.*
import org.lianye.engine.FrameCapturer
import org.lianye.engine.FrameSample
import org.lianye.engine.LianyeEngine
import org.lianye.engine.OverlapMatcher
import org.lianye.engine.TileStore
import org.lianye.engine.model.LianyeState
import org.lianye.engine.model.PixelSlice
import org.lianye.ui.common.localized
import org.lianye.ui.floating.ManualFloatingOverlayManager
import org.lianye.ui.main.MainActivity
import java.lang.ref.WeakReference

/** Owns one authorization and one virtual display. Manual sessions do not bind accessibility. */
class LianyeMediaProjectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = App.instance.appComponent.lianyeRepository
    private var currentProjection: MediaProjection? = null
    private var capturer: MediaProjectionFrameCapturer? = null
    private var manualEngine: LianyeEngine? = null
    private var captureJob: Job? = null
    private var overlay: ManualFloatingOverlayManager? = null
    private var feedback: ManualSessionFeedback? = null
    private var manual = false
    private var overlayFailed = false
    private var sessionId = 0
    @Volatile private var stopReason: CaptureCompletion? = null
    @Volatile private var destroyed = false
    private var sessionFinished = false
    private val learning get() = getSharedPreferences("manual_capture", MODE_PRIVATE)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        active = WeakReference(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (manual) stopManual(CaptureCompletion.INTERRUPTED) else { releaseProjection(); stopSelf() }
                return START_NOT_STICKY
            }
            ACTION_CAPTURE -> { beginManualCapture(); return START_NOT_STICKY }
            ACTION_FINISH -> { stopManual(CaptureCompletion.USER_STOPPED); return START_NOT_STICKY }
            ACTION_CANCEL -> { cancelPrepared(); return START_NOT_STICKY }
            ACTION_REFRESH -> { overlayFailed = false; refreshControls(); return START_NOT_STICKY }
            ACTION_START, ACTION_PREPARE_MANUAL -> Unit
            else -> { if (currentProjection == null) stopSelf(); return START_NOT_STICKY }
        }
        if (captureJob?.isActive == true) return START_NOT_STICKY
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            if (currentProjection == null) stopSelf()
            return START_NOT_STICKY
        }
        releaseProjection()
        manual = intent.action == ACTION_PREPARE_MANUAL
        overlayFailed = false
        stopReason = null
        sessionFinished = false
        sessionId++
        if (manual) {
            // Mode/session observation hides automatic controls without changing their enabled preference.
            // The old key also learned from notification captures, where no swipe guide was shown.
            feedback = ManualSessionFeedback(learning.getBoolean("floating_guide_learned", false))
            repository.updateManualSession(ManualCaptureState(phase = ManualPhase.PREPARING))
        }
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(resultCode, resultData)
            currentProjection = projection
            // The callback must be registered BEFORE createVirtualDisplay on Android 14.
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    if (currentProjection !== projection) return
                    Log.i(TAG, "Projection stopped by system")
                    MediaProjectionHolder.clear()
                    if (manual) stopManual(CaptureCompletion.PERMISSION_LOST)
                    else { releaseProjection(); stopSelf() }
                }
                override fun onCapturedContentResize(width: Int, height: Int) { capturer?.resizeContent(width, height) }
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) { capturer?.setContentVisible(isVisible) }
            }, Handler(Looper.getMainLooper()))
            MediaProjectionHolder.set(projection)
            repository.updateProjectionGranted(true)
            if (manual) prepareManualSession() else updateNotification()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Screen capture authorization unavailable", error)
            if (manual) {
                sessionFinished = true
                repository.updateManualSession(ManualCaptureState(message = "projection_failed"))
            }
            releaseProjection()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun prepareManualSession() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = ManualFloatingOverlayManager(this, wm, repository, scope,
            onStart = { beginManualCapture() }, onFinish = { stopManual(CaptureCompletion.USER_STOPPED) },
            onUnavailable = { Handler(Looper.getMainLooper()).post { overlayFailed = true; refreshControls() } })
        overlay?.setProductVisible(productVisible)
        val capture = MediaProjectionFrameCapturer(this,
            onPreCapture = { overlay?.hideBeforeCapture() ?: if (overlay == null) 0L else null },
            onPostCapture = { withContext(Dispatchers.Main.immediate) { overlay?.showAfterCapture() } })
        capturer = capture
        // Retry remains within this projection. Only the service finally releases its display.
        val retainedCapture = object : FrameCapturer by capture {
            override fun releaseSession() = Unit
            override suspend fun sampleFrame(): FrameSample = if (productVisible)
                FrameSample.Failed(CaptureCompletion.INTERRUPTED) else capture.sampleFrame()
            override suspend fun captureCleanFrame(candidate: PixelSlice): FrameSample {
                if (productVisible) return FrameSample.Failed(CaptureCompletion.INTERRUPTED)
                if (!MediaProjectionHolder.isReady()) return FrameSample.Failed(CaptureCompletion.PERMISSION_LOST)
                if (ManualCleanFramePolicy.canReuseCandidate(feedback?.state?.control ?: ManualControl.UNAVAILABLE,
                        overlay?.let { it.hasAttachedWindows || it.hasEverAttachedWindows } == true,
                        projectionReady = true, productVisible = productVisible)) {
                    // No own windows existed in the sampled frame. A quiet VD may never send a second buffer.
                    return FrameSample.Available(candidate)
                }
                val clean = capture.captureCleanFrame(candidate)
                return when {
                    productVisible -> FrameSample.Failed(CaptureCompletion.INTERRUPTED)
                    !MediaProjectionHolder.isReady() -> FrameSample.Failed(CaptureCompletion.PERMISSION_LOST)
                    else -> clean
                }
            }
        }
        val store = TileStore(App.instance.appComponent.draftStore.tilesDirectory, onCheckpoint = {
            repository.checkpointDraft(it)
            if (repository.lastPersistenceError.value != null) throw java.io.IOException("Draft checkpoint failed")
        })
        manualEngine = LianyeEngine(OverlapMatcher(screenDensity = resources.displayMetrics.density), null, store, retainedCapture)
        feedback?.ready(resolveControl())
        publish()
    }

    private fun resolveControl(): ManualControl = ManualControlPolicy.resolve(
        !overlayFailed && Settings.canDrawOverlays(this)
    )

    private fun refreshControls() {
        if (!manual || currentProjection == null) return
        val control = resolveControl()
        feedback?.control(control)
        publish()
        if (control == ManualControl.UNAVAILABLE && captureJob?.isActive == true) stopManual(CaptureCompletion.INTERRUPTED)
    }

    private fun publish() {
        if (!manual) return
        val state = feedback?.state ?: return
        repository.updateManualSession(state)
        overlay?.render(state)
        updateNotification()
    }

    private fun productVisibilityChanged(visible: Boolean) {
        overlay?.setProductVisible(visible)
        if (visible && feedback?.state?.phase in setOf(ManualPhase.TAKING_FIRST, ManualPhase.RECORDING, ManualPhase.GAP)) {
            stopManual(CaptureCompletion.INTERRUPTED)
        }
    }

    private fun beginManualCapture() {
        val current = feedback ?: return
        if (!manual || currentProjection == null || captureJob?.isActive == true || productVisible) return
        if (current.state.phase != ManualPhase.READY && current.state.phase != ManualPhase.FIRST_FAILED) return
        refreshControls()
        if (current.state.control == ManualControl.UNAVAILABLE) return
        val engine = manualEngine ?: return
        stopReason = null
        current.begin()
        publish()
        val owner = sessionId
        captureJob = scope.launch(Dispatchers.IO) {
            if (stopReason != null && stopReason != CaptureCompletion.USER_STOPPED) {
                withContext(Dispatchers.Main) { finishSession(false, stopReason ?: CaptureCompletion.INTERRUPTED) }
                return@launch
            }
            if (!repository.startWeaving()) {
                withContext(Dispatchers.Main) { finishSession(false, CaptureCompletion.INTERRUPTED, cancelled = true) }
                return@launch
            }
            val tiles = engine.startManualWeaving(onProgress = { progress ->
                repository.updateProgress(progress.frameCount, progress.stitchedHeightPx, progress.viewportHeightPx)
                scope.launch {
                    if (owner != sessionId || feedback !== current) return@launch
                    current.accepted(progress.frameCount)
                    if (current.learnedThisSession && current.state.control == ManualControl.OVERLAY)
                        learning.edit().putBoolean("floating_guide_learned", true).apply()
                    if (current.finishRequested && progress.frameCount > 0) engine.stop(stopReason ?: CaptureCompletion.USER_STOPPED)
                    publish()
                }
            }, onUnmatched = { unmatched ->
                scope.launch {
                    if (owner != sessionId || feedback !== current) return@launch
                    current.temporaryUnmatched(unmatched)
                    publish()
                }
            }, onReverse = {
                scope.launch {
                    if (owner != sessionId || feedback !== current) return@launch
                    current.reversed()
                    publish()
                }
            })
            if (tiles.isEmpty() && stopReason == null && MediaProjectionHolder.isReady() && !destroyed &&
                engine.completionReason != CaptureCompletion.PERMISSION_LOST && engine.completionReason != CaptureCompletion.STORAGE_FULL) {
                repository.failWeaving("first_frame_failed", tiles, engine.completionReason)
                withContext(Dispatchers.Main) { if (owner == sessionId) { current.failedFirst(); publish() } }
            } else {
                if (engine.state.value == LianyeState.COMPLETED) repository.finishWeaving(tiles, engine.completionReason)
                else repository.failWeaving(engine.failureMessage ?: "capture_interrupted", tiles, engine.completionReason)
                withContext(NonCancellable + Dispatchers.Main) { if (owner == sessionId) finishSession(tiles.isNotEmpty(), engine.completionReason) }
            }
        }
    }

    private fun stopManual(reason: CaptureCompletion) {
        if (!manual) return
        if (stopReason == null || reason != CaptureCompletion.USER_STOPPED) stopReason = reason
        val current = feedback ?: return
        if (captureJob?.isActive == true) {
            val readyToStop = current.finish()
            publish()
            // Waiting for the SAME first-frame callback prevents engine initialization from erasing Stop.
            if (readyToStop || reason != CaptureCompletion.USER_STOPPED) manualEngine?.stop(reason)
        } else finishSession(repository.draft.value != null, reason)
    }

    private fun cancelPrepared() {
        if (!manual) return
        if (captureJob?.isActive == true) { stopManual(CaptureCompletion.USER_STOPPED); return }
        finishSession(false, CaptureCompletion.USER_STOPPED, cancelled = true)
    }

    private fun finishSession(hasContent: Boolean, reason: CaptureCompletion, cancelled: Boolean = false) {
        sessionFinished = true
        val wasOverlay = feedback?.state?.control == ManualControl.OVERLAY
        val message = when {
            cancelled -> null
            !hasContent -> "no_frame_captured"
            reason == CaptureCompletion.PERMISSION_LOST || reason == CaptureCompletion.INTERRUPTED -> "capture_interrupted"
            else -> null
        }
        overlay?.hide()
        releaseProjection()
        repository.updateManualSession(ManualCaptureState(message = message))
        if (hasContent && wasOverlay && stopReason == CaptureCompletion.USER_STOPPED && !destroyed) {
            try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_draft", true)) }
            catch (_: RuntimeException) { /* The retained draft remains available from the app. */ }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseProjection() {
        val projection = currentProjection
        currentProjection = null
        overlay?.hide(); overlay = null
        capturer?.release(); capturer = null
        manualEngine = null
        MediaProjectionHolder.clear()
        runCatching { projection?.stop() }
        // Automatic API30+ takeScreenshot has no dependency on this projection authorization.
        repository.updateProjectionGranted(Build.VERSION.SDK_INT >= 30)
        feedback = null
    }

    override fun onDestroy() {
        destroyed = true
        overlay?.hide()
        if (manual && !sessionFinished && captureJob?.isActive == true) {
            stopReason = CaptureCompletion.INTERRUPTED
            feedback?.finish()
            manualEngine?.stop(CaptureCompletion.INTERRUPTED)
            // The IO owner must close and checkpoint valid image rows before resources are released.
            captureJob?.invokeOnCompletion { Handler(Looper.getMainLooper()).post {
                releaseProjection(); repository.updateManualSession(ManualCaptureState(message = "capture_interrupted")); scope.cancel()
            } }
        } else {
            releaseProjection()
            if (manual && !sessionFinished) repository.updateManualSession(ManualCaptureState(message = "capture_interrupted"))
            scope.cancel()
        }
        if (active?.get() === this) active = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, localized("连页屏幕捕获", "Lianye screen capture"), NotificationManager.IMPORTANCE_LOW
        ).apply { description = localized("本次长截图状态", "Current long screenshot status"); setShowBadge(false) })
    }

    private fun buildNotification(): Notification {
        val state = feedback?.state ?: ManualCaptureState(phase = ManualPhase.PREPARING)
        val title = if (!manual) localized("连页屏幕捕获已开启", "Lianye screen capture is ready") else when (state.phase) {
            ManualPhase.RECORDING, ManualPhase.GAP -> localized("连页 · 正在截图", "Lianye · Capturing")
            ManualPhase.TAKING_FIRST -> localized("正在截取首屏", "Capturing first screen")
            ManualPhase.FIRST_FAILED -> localized("未截到画面", "No image captured")
            ManualPhase.FINISHING -> localized("正在收尾", "Finishing")
            ManualPhase.READY -> if (state.control == ManualControl.OVERLAY) localized("连页 · 准备好了", "Lianye · Ready")
                else localized("等待悬浮球权限", "Waiting for floating control permission")
            else -> localized("连页 · 正在准备", "Lianye · Preparing")
        }
        val copy = if (!manual) localized("点按返回连页", "Tap to return to Lianye") else when (state.phase) {
            ManualPhase.READY -> if (state.control == ManualControl.OVERLAY)
                localized("打开要截的页面，点悬浮球上的「开始截图」。", "Open the page and tap Capture on the floating control.")
                else localized("返回连页，允许悬浮球后继续。", "Return to Lianye and allow the floating control to continue.")
            ManualPhase.FIRST_FAILED -> localized("点悬浮球上的「重新截图」。", "Tap Try again on the floating control.")
            ManualPhase.GAP -> localized("未能接上，已保留前段。", "Unable to join. Previous content is kept.")
            ManualPhase.RECORDING -> if (state.message == "temporarily_unmatched")
                localized("暂未接上，向下滑回一点，停稳后继续。", "Not joined yet. Swipe down a little, then pause.")
                else localized("截完点悬浮球上的「结束」。", "Tap Finish on the floating control when done.")
            else -> localized("保留已截内容，请稍候。", "Keeping captured content. Please wait.")
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(copy).setStyle(NotificationCompat.BigTextStyle().bigText(copy))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setPriority(NotificationCompat.PRIORITY_LOW).setOnlyAlertOnce(true).setOngoing(true)
        return builder.build()
    }

    private fun updateNotification() {
        if (destroyed || currentProjection == null) return
        try { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification()) }
        catch (_: SecurityException) { /* Notification permission may change during the session. */ }
    }

    companion object {
        private const val TAG = "LianyeProjection"
        const val CHANNEL_ID = "lianye_projection_channel"
        private const val NOTIFICATION_ID = 2001
        private var active: WeakReference<LianyeMediaProjectionService>? = null
        @Volatile private var productVisible = true
        const val ACTION_START = "org.lianye.action.START_PROJECTION"
        const val ACTION_STOP = "org.lianye.action.STOP_PROJECTION"
        const val ACTION_PREPARE_MANUAL = "org.lianye.action.PREPARE_MANUAL"
        const val ACTION_CAPTURE = "org.lianye.action.CAPTURE_MANUAL"
        const val ACTION_FINISH = "org.lianye.action.FINISH_MANUAL"
        const val ACTION_CANCEL = "org.lianye.action.CANCEL_MANUAL"
        private const val ACTION_REFRESH = "org.lianye.action.REFRESH_MANUAL_CONTROLS"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        fun start(context: Context, resultCode: Int, data: Intent) = startProjection(context, resultCode, data, manual = false)
        fun startManual(context: Context, resultCode: Int, data: Intent) = startProjection(context, resultCode, data, manual = true)
        private fun startProjection(context: Context, resultCode: Int, data: Intent, manual: Boolean) {
            ContextCompat.startForegroundService(context, Intent(context, LianyeMediaProjectionService::class.java).apply {
                action = if (manual) ACTION_PREPARE_MANUAL else ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode); putExtra(EXTRA_RESULT_DATA, data)
            })
        }
        fun refreshManualControls(context: Context) = command(context, ACTION_REFRESH)
        /** Cancels only an unstarted session, synchronously before a mode change. */
        @androidx.annotation.MainThread
        fun cancelPreparationForModeChange(): Boolean {
            val service = active?.get() ?: return false
            if (!service.manual || service.captureJob?.isActive == true ||
                service.feedback?.state?.phase !in listOf(ManualPhase.READY, ManualPhase.FIRST_FAILED)) return false
            service.cancelPrepared()
            return true
        }
        fun finishManual(context: Context) = command(context, ACTION_FINISH)
        fun cancelManual(context: Context) = command(context, ACTION_CANCEL)
        fun stop(context: Context) = command(context, ACTION_STOP)
        private fun command(context: Context, action: String) {
            if (active?.get() == null) return
            try { context.startService(Intent(context, LianyeMediaProjectionService::class.java).setAction(action)) }
            catch (error: RuntimeException) { Log.w(TAG, "Capture command unavailable", error) }
        }
        fun setProductVisible(visible: Boolean) {
            productVisible = visible
            Handler(Looper.getMainLooper()).post { active?.get()?.productVisibilityChanged(visible) }
        }
    }
}
