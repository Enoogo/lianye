package org.lianye.ui.main

import android.content.*
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lianye.App
import org.lianye.domain.model.*
import org.lianye.engine.export.MediaExportManager
import org.lianye.platform.CaptureRegionStore
import org.lianye.platform.DeviceVendorDetector
import org.lianye.platform.RestrictedSettingsHelper
import org.lianye.platform.RestrictionStatus
import org.lianye.platform.AccessibilityHelpState
import org.lianye.platform.AccessibilitySettingsNavigator
import org.lianye.service.LianyeAccessibilityService
import org.lianye.service.capture.LianyeMediaProjectionService
import org.lianye.service.capture.MediaProjectionHolder
import org.lianye.ui.common.localized
import org.lianye.ui.common.uiText
import org.lianye.ui.common.theme.LianyeTheme
import org.lianye.ui.main.components.AutomaticCapturePreparationSheet
import org.lianye.ui.main.components.ReplaceDraftDialog
import org.lianye.ui.main.components.ManualCaptureControlSheet
import org.lianye.ui.preview.ScrollPreviewScreen

/** Routes capability setup; capture and export remain owned by their application services. */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val repository get() = App.instance.appComponent.lianyeRepository
    private var viewingDraft by mutableStateOf(false)
    private var showSetupGuide by mutableStateOf(false)
    private var showManualControls by mutableStateOf(false)
    private var confirmNewCapture by mutableStateOf(false)
    private var isRestrictedBlocked by mutableStateOf(false)
    private var restrictionStatus by mutableStateOf(RestrictionStatus.NOT_APPLICABLE)
    private var accessibilityEnabled by mutableStateOf(false)
    private var hasAttemptedEnable by mutableStateOf(false)
    private var hasEncounteredRestriction by mutableStateOf(false)
    private var pendingManualLaunch by mutableStateOf(false)
    private var resumed by mutableStateOf(false)
    private var captureRegion by mutableStateOf(Rect())
    private var waitingForAutomaticSettings = false
    private var waitingForManualOverlaySettings = false
    private var pendingProjectionMode = CaptureMode.AUTO

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            if (pendingProjectionMode == CaptureMode.MANUAL) {
                pendingManualLaunch = true
                LianyeMediaProjectionService.startManual(this, result.resultCode, result.data!!)
            } else {
                continueAutomaticAfterProjection = true
                LianyeMediaProjectionService.start(this, result.resultCode, result.data!!)
                if (!repository.isServiceConnected.value) openAccessibilitySettings(continueCapture = true)
            }
        } else {
            pendingManualLaunch = false
            continueAutomaticAfterProjection = false
            toast(localized("未授予屏幕捕获权限", "Screen capture permission was declined"))
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getPreferences(MODE_PRIVATE)
        hasAttemptedEnable = prefs.getBoolean("attempted", false)
        hasEncounteredRestriction = prefs.getBoolean("restricted", false)
        val storedMode = runCatching { CaptureMode.valueOf(prefs.getString("capture_mode", "AUTO")!!) }.getOrDefault(CaptureMode.AUTO)
        repository.setCaptureMode(storedMode)
        pendingProjectionMode = runCatching { CaptureMode.valueOf(savedInstanceState?.getString("projection_mode") ?: "AUTO") }.getOrDefault(CaptureMode.AUTO)
        pendingManualLaunch = savedInstanceState?.getBoolean("manual_launch") ?: false
        waitingForAutomaticSettings = savedInstanceState?.getBoolean("automatic_settings") ?: false
        waitingForManualOverlaySettings = savedInstanceState?.getBoolean("manual_overlay_settings") ?: false
        showManualControls = savedInstanceState?.getBoolean("manual_controls") ?: false
        viewingDraft = savedInstanceState?.getBoolean("view_draft") ?: intent.getBooleanExtra("open_draft", false)
        setContent {
            LianyeTheme {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = colors.background.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = colors.background.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        val light = colors.background.luminance() > 0.5f
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                Box(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
                    val state by repository.weavingState.collectAsState()
                    val connected by repository.isServiceConnected.collectAsState()
                    LaunchedEffect(connected, resumed) {
                        if (resumed) accessibilityEnabled = AccessibilitySettingsNavigator.isServiceEnabled(this@MainActivity)
                    }
                    val projection by repository.isProjectionGranted.collectAsState()
                    val overlay by repository.isOverlayVisible.collectAsState()
                    val draft by repository.draft.collectAsState()
                    val mode by repository.captureMode.collectAsState()
                    val manual by repository.manualSession.collectAsState()
                    val persistenceError by repository.lastPersistenceError.collectAsState()
                    val exporter = App.instance.exportCoordinator
                    val exportState by exporter.state.collectAsState()
                    val activeDraft = draft
                    val capturing = state is WeavingState.Weaving
                    val busy = capturing || exportState.busy

                    BackHandler(enabled = viewingDraft) { viewingDraft = false }
                    LaunchedEffect(activeDraft?.id) { if (activeDraft == null) viewingDraft = false }
                    LaunchedEffect(manual.phase, manual.control, resumed, pendingManualLaunch) {
                        when (manual.phase) {
                            ManualPhase.READY, ManualPhase.FIRST_FAILED -> if (pendingManualLaunch && resumed) {
                                if (manual.control == ManualControl.UNAVAILABLE) showManualControls = true
                                else {
                                    pendingManualLaunch = false
                                    showManualControls = false
                                    leaveForTarget()
                                }
                            }
                            ManualPhase.TAKING_FIRST, ManualPhase.RECORDING, ManualPhase.GAP, ManualPhase.FINISHING -> pendingManualLaunch = false
                            else -> Unit
                        }
                    }
                    // API29 starts its projection service asynchronously after the consent result.
                    LaunchedEffect(projection, connected, mode, resumed, continueAutomaticAfterProjection) {
                        if (mode == CaptureMode.AUTO && projection && connected && pendingProjectionMode == CaptureMode.AUTO && resumed && !showSetupGuide && !viewingDraft && activeDraft == null) {
                            if (continueAutomaticAfterProjection) {
                                continueAutomaticAfterProjection = false
                                showAutomaticControlsAndLeave()
                            }
                        }
                    }
                    LaunchedEffect(exportState.shareUris) {
                        if (exportState.shareUris.isNotEmpty()) {
                            val uris = exporter.consumeShare()
                            try {
                                startActivity(Intent.createChooser(MediaExportManager(contentResolver).createShareIntent(uris), localized("分享长图", "Share image")))
                            } catch (_: RuntimeException) { exporter.shareFailed() }
                        }
                    }
                    LaunchedEffect(exportState.startNewAfterSave) {
                        if (exportState.startNewAfterSave) {
                            exporter.consumeNewCapture()
                            clearDraftAndContinue()
                        }
                    }

                    if (viewingDraft && activeDraft != null && !capturing) {
                        ScrollPreviewScreen(
                            draft = activeDraft,
                            onDraftChange = { changed ->
                                if (!exportState.busy && repository.adoptDraft(changed)) {
                                    exporter.clearMessage()
                                    App.instance.persistCurrentDraft()
                                }
                            },
                            onSaveClick = { split -> repository.draft.value?.let { exporter.export(it, split, share = false) } },
                            onShareClick = { split -> repository.draft.value?.let { exporter.export(it, split, share = true) } },
                            onBackClick = { viewingDraft = false },
                            onDoneClick = { viewingDraft = false },
                            isExporting = exportState.busy,
                            exportProgress = exportState.progress,
                            onOpenGalleryClick = exportState.savedUris.firstOrNull()?.let { uri -> {
                                try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                catch (_: RuntimeException) { toast(localized("图片已保存，可在系统相册中查看。", "Image saved. Open your gallery to view it.")) }
                            } },
                            exportMessage = if (persistenceError != null) uiText("草稿暂时无法写入存储，请先保存或分享。", "Draft storage is unavailable. Save or share first.") else exportState.message,
                            onViewportChange = { offset, scale ->
                                repository.adoptViewport(activeDraft.id, offset, scale)
                                App.instance.persistCurrentDraft()
                            }
                        )
                    } else {
                        val uiState = MainUiState.create(state, projection, hasAttemptedEnable, hasEncounteredRestriction, isRestrictedBlocked, DeviceVendorDetector.getPreFlightHintRes()).copy(
                            isServiceConnected = connected,
                            isCapturing = capturing,
                            errorMessage = if (persistenceError != null) uiText("草稿存储失败，请检查可用空间。", "Unable to store the draft. Check free space.")
                                else when (manual.message) {
                                    "projection_failed" -> uiText("屏幕捕获未能开启，请重试。", "Screen capture could not start. Try again.")
                                    "first_frame_failed", "no_frame_captured" -> uiText("未截到画面，可以重新开始。", "No image captured. Start again.")
                                    "capture_interrupted" -> uiText("截图已中断，已有内容已保留。", "Capture interrupted. Captured content is kept.")
                                    else -> if (state is WeavingState.Error) uiText("截图已中断，已有内容已保留。", "Capture interrupted. Captured content is kept.") else null
                                }
                        )
                        MainScreen(
                            uiState = uiState, hasDraft = activeDraft != null && !capturing,
                            isDraftSaved = activeDraft?.isSaved == true, isOverlayVisible = overlay,
                            draft = activeDraft, captureMode = mode, manualSession = manual,
                            isBusy = busy,
                            accessibilityHelpState = AccessibilityHelpState.resolve(
                                connected, accessibilityEnabled, restrictionStatus, Build.VERSION.SDK_INT),
                            onPrimaryAction = {
                                if (!busy) requestNewCapture()
                            },
                            onResumeDraft = { if (!capturing) viewingDraft = true },
                            onNewCapture = { requestNewCapture() },
                            onSelectCaptureMode = { if (!busy) selectMode(it) },
                            onOpenAccessibilitySettings = { openAccessibilitySettings() },
                            onCancelPreparation = { cancelManualPreparation() },
                            onHideOverlay = { LianyeAccessibilityService.hideControls() },
                            onStopService = {
                                if (!busy) {
                                    pendingManualLaunch = false
                                    LianyeMediaProjectionService.stop(this@MainActivity)
                                    LianyeAccessibilityService.hideControls()
                                    if (connected) LianyeAccessibilityService.disableCurrentService()
                                }
                            },
                            onOpenAppDetailsSettings = { openAppDetailsSettings() },
                            onOpenBatteryOptimization = { openBatterySettings() },
                            onCopyAdbCommand = { copyAdbCommand() },
                            captureRegionLeft = captureRegion.left,
                            captureRegionTop = captureRegion.top,
                            captureRegionRight = captureRegion.right,
                            captureRegionBottom = captureRegion.bottom,
                            onCaptureRegionEdited = { left, top, right, bottom ->
                                val clamped = CaptureRegionStore.clamp(
                                    Rect(left, top, right, bottom),
                                    CaptureRegionStore.fullFrame(this@MainActivity)
                                )
                                CaptureRegionStore.save(this@MainActivity, clamped)
                                captureRegion = clamped
                            },
                            onEditCaptureRegion = {
                                // Open the editor directly; the bubble returns after Done/Cancel.
                                if (!busy && LianyeAccessibilityService.openRegionEditor()) leaveForTarget()
                                else showSetupGuide = true
                            }
                        )
                    }

                    if (showSetupGuide) {
                        AutomaticCapturePreparationSheet(
                            needsProjection = Build.VERSION.SDK_INT == 29 && !projection,
                            accessibilityHelpState = AccessibilityHelpState.resolve(
                                connected, accessibilityEnabled, restrictionStatus, Build.VERSION.SDK_INT),
                            onDismissRequest = { showSetupGuide = false },
                            onConfirm = {
                                showSetupGuide = false
                                if (Build.VERSION.SDK_INT == 29 && !repository.isProjectionGranted.value) requestProjection(CaptureMode.AUTO)
                                else if (isRestrictedBlocked && !repository.isServiceConnected.value && !accessibilityEnabled) openAppDetailsSettings()
                                else openAccessibilitySettings(continueCapture = true)
                            }
                        )
                    }
                    if (showManualControls && mode == CaptureMode.MANUAL && manual.phase in listOf(ManualPhase.IDLE, ManualPhase.READY, ManualPhase.FIRST_FAILED)) {
                        ManualCaptureControlSheet(
                            onDismissRequest = { cancelManualPreparation() },
                            onEnableOverlay = { openOverlaySettings() }
                        )
                    }
                    if (confirmNewCapture && activeDraft != null) {
                        ReplaceDraftDialog(
                            onDismiss = { confirmNewCapture = false },
                            onSave = {
                                confirmNewCapture = false
                                viewingDraft = true
                                repository.draft.value?.let { exporter.export(it, it.crop.height > 30_000, share = false, startNewAfterSave = true) }
                            },
                            onDiscard = { confirmNewCapture = false; clearDraftAndContinue() }
                        )
                    }
                }
            }
        }
    }

    private var continueAutomaticAfterProjection by mutableStateOf(false)

    private fun selectMode(mode: CaptureMode): Boolean {
        if (mode == repository.captureMode.value) return true
        if (mode != repository.captureMode.value) {
            if (repository.weavingState.value is WeavingState.Weaving || App.instance.exportCoordinator.state.value.busy) return false
            if (repository.manualSession.value.phase != ManualPhase.IDLE && !LianyeMediaProjectionService.cancelPreparationForModeChange()) return false
            pendingManualLaunch = false
            showManualControls = false
            showSetupGuide = false
            waitingForManualOverlaySettings = false
            waitingForAutomaticSettings = false
            continueAutomaticAfterProjection = false
        }
        if (!repository.setCaptureMode(mode)) return false
        getPreferences(MODE_PRIVATE).edit().putString("capture_mode", mode.name).apply()
        return true
    }

    private fun requestNewCapture() {
        if (App.instance.exportCoordinator.state.value.busy || repository.weavingState.value is WeavingState.Weaving) return
        if (repository.manualSession.value.phase != ManualPhase.IDLE) {
            if (repository.captureMode.value == CaptureMode.MANUAL && repository.manualSession.value.phase in listOf(ManualPhase.READY, ManualPhase.FIRST_FAILED)) prepareCapture()
            return
        }
        if (repository.draft.value?.isSaved == false) confirmNewCapture = true
        else if (repository.draft.value != null) clearDraftAndContinue()
        else prepareCapture()
    }

    private fun prepareCapture() {
        viewingDraft = false
        if (repository.captureMode.value == CaptureMode.MANUAL) {
            when (repository.manualSession.value.phase) {
                ManualPhase.READY, ManualPhase.FIRST_FAILED -> {
                    if (repository.manualSession.value.control == ManualControl.UNAVAILABLE) {
                        pendingManualLaunch = true; showManualControls = true
                    } else leaveForTarget()
                }
                ManualPhase.IDLE -> {
                    if (Settings.canDrawOverlays(this)) requestProjection(CaptureMode.MANUAL)
                    else showManualControls = true
                }
                else -> Unit
            }
        } else if (!repository.isServiceConnected.value || !repository.isProjectionGranted.value) showSetupGuide = true
        else showAutomaticControlsAndLeave()
    }

    private fun showAutomaticControlsAndLeave() {
        if (LianyeAccessibilityService.showControls()) leaveForTarget() else showSetupGuide = true
    }
    private fun leaveForTarget() = startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    private fun cancelManualPreparation() {
        showManualControls = false
        pendingManualLaunch = false
        waitingForManualOverlaySettings = false
        LianyeMediaProjectionService.cancelManual(this)
    }

    private fun clearDraftAndContinue() {
        if (App.instance.exportCoordinator.state.value.busy || repository.weavingState.value is WeavingState.Weaving) return
        App.instance.queueDraftTask {
            if (repository.discardDraft()) withContext(Dispatchers.Main) {
                App.instance.exportCoordinator.clearMessage()
                viewingDraft = false
                if (!isDestroyed && !isFinishing) prepareCapture()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_draft", false)) { pendingManualLaunch = false; viewingDraft = true }
        else if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && repository.draft.value?.isSaved == true) {
            viewingDraft = false
        }
    }
    override fun onStart() {
        super.onStart()
        LianyeMediaProjectionService.setProductVisible(true)
        LianyeAccessibilityService.setPreviewVisible(true)
    }
    override fun onStop() {
        LianyeMediaProjectionService.setProductVisible(false)
        LianyeAccessibilityService.setPreviewVisible(false)
        super.onStop()
    }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onResume() {
        super.onResume()
        resumed = true
        captureRegion = CaptureRegionStore.load(this)
        repository.updateServiceConnected(LianyeAccessibilityService.isConnected())
        if (Build.VERSION.SDK_INT == 29 && repository.captureMode.value == CaptureMode.AUTO) repository.updateProjectionGranted(MediaProjectionHolder.get() != null)
        restrictionStatus = RestrictedSettingsHelper.readStatus(this)
        isRestrictedBlocked = restrictionStatus == RestrictionStatus.BLOCKED
        accessibilityEnabled = AccessibilitySettingsNavigator.isServiceEnabled(this)
        if (isRestrictedBlocked && hasAttemptedEnable) hasEncounteredRestriction = true
        getPreferences(MODE_PRIVATE).edit().putBoolean("attempted", hasAttemptedEnable).putBoolean("restricted", hasEncounteredRestriction).apply()
        if (repository.manualSession.value.phase != ManualPhase.IDLE) LianyeMediaProjectionService.refreshManualControls(this)
        if (waitingForManualOverlaySettings) {
            waitingForManualOverlaySettings = false
            if (repository.captureMode.value == CaptureMode.MANUAL && Settings.canDrawOverlays(this)) {
                showManualControls = false
                when (repository.manualSession.value.phase) {
                    ManualPhase.IDLE -> requestProjection(CaptureMode.MANUAL)
                    ManualPhase.READY, ManualPhase.FIRST_FAILED -> {
                        pendingManualLaunch = true
                        LianyeMediaProjectionService.refreshManualControls(this)
                    }
                    else -> Unit
                }
            }
        }
        if (waitingForAutomaticSettings) {
            waitingForAutomaticSettings = false
            if (repository.captureMode.value == CaptureMode.AUTO && repository.isServiceConnected.value) {
                if (repository.isProjectionGranted.value) showAutomaticControlsAndLeave()
                else requestProjection(CaptureMode.AUTO)
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("view_draft", viewingDraft)
        outState.putBoolean("manual_launch", pendingManualLaunch)
        outState.putBoolean("automatic_settings", waitingForAutomaticSettings)
        outState.putBoolean("manual_overlay_settings", waitingForManualOverlaySettings)
        outState.putBoolean("manual_controls", showManualControls)
        outState.putString("projection_mode", pendingProjectionMode.name)
        super.onSaveInstanceState(outState)
    }
    private fun requestProjection(mode: CaptureMode) {
        pendingProjectionMode = mode
        continueAutomaticAfterProjection = mode == CaptureMode.AUTO
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
    private fun openAccessibilitySettings(continueCapture: Boolean = false) {
        if (!selectMode(CaptureMode.AUTO)) return
        hasAttemptedEnable = true
        getPreferences(MODE_PRIVATE).edit().putBoolean("attempted", true).apply()
        waitingForAutomaticSettings = continueCapture
        when (AccessibilitySettingsNavigator.open(this)) {
            AccessibilitySettingsNavigator.Destination.UNAVAILABLE -> {
                waitingForAutomaticSettings = false
                toast(localized("无法打开系统设置，请在设置中搜索「无障碍」。", "Could not open Settings. Search for Accessibility in system settings."))
            }
            AccessibilitySettingsNavigator.Destination.SETTINGS ->
                toast(localized("在系统设置中搜索「无障碍」，找到连页。", "Search Settings for Accessibility and find Lianye."))
            else -> Unit
        }
    }
    private fun openOverlaySettings() {
        waitingForManualOverlaySettings = true
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        catch (_: RuntimeException) { openAppDetailsSettings() }
    }
    private fun openAppDetailsSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }.recoverCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }.onFailure {
            toast(localized("无法打开应用信息，请在系统设置中找到连页。", "Could not open app information. Find Lianye in system settings."))
        }
    }
    private fun openBatterySettings() {
        try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        catch (_: RuntimeException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    private fun copyAdbCommand() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Lianye", "adb shell appops set $packageName ACCESS_RESTRICTED_SETTINGS allow"))
        toast(localized("命令已复制", "Command copied"))
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
