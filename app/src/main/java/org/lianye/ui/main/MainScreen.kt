package org.lianye.ui.main

import android.os.Build
import org.lianye.platform.AccessibilityHelpState
import org.lianye.platform.RestrictionStatus

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.lianye.R
import org.lianye.domain.model.CaptureMode
import org.lianye.domain.model.Draft
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualPhase
import org.lianye.ui.common.theme.LianyeTheme
import org.lianye.ui.common.uiText
import org.lianye.ui.common.LianyePrimaryButton
import org.lianye.ui.main.components.AboutBottomSheet
import org.lianye.ui.main.components.CaptureModeSelector
import org.lianye.ui.main.components.MainTopBar
import org.lianye.ui.main.components.HomeEmptyHero
import org.lianye.ui.preview.PreviewChunk
import org.lianye.ui.preview.PreviewTileCache
import kotlin.math.roundToInt

/** One task at a time: prepare capture, or resume the single retained draft. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun MainScreen(
    uiState: MainUiState,
    modifier: Modifier = Modifier,
    hasDraft: Boolean = false,
    isDraftSaved: Boolean = false,
    isOverlayVisible: Boolean = false,
    onPrimaryAction: () -> Unit,
    onResumeDraft: () -> Unit,
    onNewCapture: () -> Unit,
    onHideOverlay: () -> Unit,
    onStopService: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    draft: Draft? = null,
    captureMode: CaptureMode = CaptureMode.AUTO,
    isBusy: Boolean = false,
    onSelectCaptureMode: (CaptureMode) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    manualSession: ManualCaptureState = ManualCaptureState(),
    onCancelPreparation: () -> Unit = {},
    captureRegionLeft: Int = 0,
    captureRegionTop: Int = 0,
    captureRegionRight: Int = 0,
    captureRegionBottom: Int = 0,
    onCaptureRegionEdited: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onEditCaptureRegion: () -> Unit = {},
    accessibilityHelpState: AccessibilityHelpState = AccessibilityHelpState.resolve(
        uiState.isServiceConnected, false, RestrictionStatus.UNKNOWN, Build.VERSION.SDK_INT)
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val currentDraft = (draft != null || hasDraft) && !(draft?.isSaved ?: isDraftSaved)
    val busy = isBusy || uiState.isCapturing
    val preparing = captureMode == CaptureMode.MANUAL && manualSession.phase in listOf(ManualPhase.PREPARING, ManualPhase.READY, ManualPhase.FIRST_FAILED)
    val modeBusy = busy || manualSession.phase !in listOf(ManualPhase.IDLE, ManualPhase.READY, ManualPhase.FIRST_FAILED)
    val ready = uiState.isServiceConnected && !uiState.showMediaProjectionCard
    val label = when {
        currentDraft -> stringResource(R.string.home_continue_draft)
        uiState.isCapturing -> uiText("截图进行中", "Capturing")
        preparing && manualSession.phase == ManualPhase.PREPARING -> uiText("正在准备", "Preparing")
        preparing && manualSession.phase == ManualPhase.FIRST_FAILED -> uiText("去重试", "Retry on the page")
        preparing -> uiText("去截图", "Go capture")
        captureMode == CaptureMode.MANUAL -> stringResource(R.string.home_enable)
        ready -> uiText("去截图", "Go capture")
        else -> stringResource(R.string.home_enable)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MainTopBar(onShowAbout = { showAbout = true }) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureModeSelector(captureMode, !modeBusy, onSelectCaptureMode)
                    LianyePrimaryButton(
                        onClick = { if (!busy) { if (currentDraft) onResumeDraft() else onPrimaryAction() } },
                        enabled = !busy && manualSession.phase != ManualPhase.PREPARING, modifier = Modifier.fillMaxWidth()
                    ) { Text(label) }
                    if (currentDraft) {
                        TextButton(onClick = onNewCapture, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.home_new_capture))
                        }
                    } else if (preparing) {
                        TextButton(onClick = onCancelPreparation, enabled = !isBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(uiText("取消准备", "Cancel preparation"))
                        }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val thumbnailHeight = (maxHeight - 150.dp).coerceIn(164.dp, 320.dp)
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = if (currentDraft) 0.dp else maxHeight)
                    .padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 20.dp),
                verticalArrangement = if (currentDraft) Arrangement.spacedBy(16.dp)
                    else Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = if (currentDraft) Alignment.Start else Alignment.CenterHorizontally
            ) {
                if (currentDraft) {
                    Text(uiText("继续上次截图", "Continue your capture"), style = MaterialTheme.typography.headlineSmall)
                    Card(
                        onClick = { if (!busy) onResumeDraft() }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        if (draft != null) DraftThumbnail(draft, Modifier.fillMaxWidth().height(thumbnailHeight))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    if (draft?.isSaved ?: isDraftSaved) uiText("已保存", "Saved") else uiText("未保存", "Unsaved"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (draft != null) Text("${draft.crop.width} × ${draft.crop.height}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    HomeEmptyHero(onClick = if (busy) null else onEditCaptureRegion)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiText("截图范围", "Capture area"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("┌", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CoordinateField(captureRegionLeft) { value ->
                                onCaptureRegionEdited(value, captureRegionTop, captureRegionRight, captureRegionBottom)
                            }
                            CoordinateField(captureRegionTop) { value ->
                                onCaptureRegionEdited(captureRegionLeft, value, captureRegionRight, captureRegionBottom)
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("┘", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CoordinateField(captureRegionRight) { value ->
                                onCaptureRegionEdited(captureRegionLeft, captureRegionTop, value, captureRegionBottom)
                            }
                            CoordinateField(captureRegionBottom) { value ->
                                onCaptureRegionEdited(captureRegionLeft, captureRegionTop, captureRegionRight, value)
                            }
                        }
                        TextButton(
                            onClick = onEditCaptureRegion,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(uiText("框选截图范围", "Set capture area")) }
                    }
                }
                if (uiState.isCapturing) Text(
                    uiText("返回要截的页面，点悬浮球上的「结束」。", "Return to the page and tap Finish on the floating control."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (preparing) Text(
                    when (manualSession.phase) {
                        ManualPhase.PREPARING -> uiText("正在准备本次屏幕捕获。", "Preparing this screen capture session.")
                        ManualPhase.FIRST_FAILED -> uiText("未截到画面。返回目标页面，点「重试」，或取消本次截图。", "No image was captured. Return to the page and tap Retry, or cancel this session.")
                        else -> uiText("准备好了。打开要截的页面，点悬浮球上的「开始截图」。", "Ready. Open the page and tap Capture on the floating control.")
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (showAbout) AboutBottomSheet(
        onDismissRequest = { showAbout = false },
        onOpenAppDetailsSettings = onOpenAppDetailsSettings,
        onOpenBatteryOptimization = onOpenBatteryOptimization,
        onCopyAdbCommand = onCopyAdbCommand,
        isServiceConnected = uiState.isServiceConnected,
        isOverlayVisible = isOverlayVisible,
        onHideOverlay = { showAbout = false; onHideOverlay() },
        onStopService = { showAbout = false; if (captureMode == CaptureMode.MANUAL) onCancelPreparation() else onStopService() },
        captureMode = captureMode,
        isBusy = busy,
        isModeBusy = modeBusy,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        accessibilityHelpState = accessibilityHelpState,
        onUseManualCapture = { showAbout = false; onSelectCaptureMode(CaptureMode.MANUAL) }
    )
}

@Composable
private fun DraftThumbnail(draft: Draft, modifier: Modifier) {
    val cache = remember(draft.id) { PreviewTileCache(12 * 1024 * 1024) }
    val crop = draft.crop
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // Capture one immutable layout snapshot: the mutable size can change before the producer starts.
    val measuredSize = viewport
    val scale = measuredSize.width.toFloat() / crop.width.coerceAtLeast(1)
    val visibleBottom = minOf(crop.bottom.toFloat(), crop.top + measuredSize.height / scale.coerceAtLeast(.0001f))
    val chunks by produceState<List<PreviewChunk>>(emptyList(), draft.id, crop, measuredSize) {
        if (measuredSize.width == 0 || measuredSize.height == 0) return@produceState
        value = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            runCatching { cache.visible(draft, crop.top.toFloat(), visibleBottom, scale) { context.ensureActive() } }
                .onFailure { android.util.Log.w("LianyeThumbnail", "Decode failed", it) }.getOrDefault(emptyList())
        }
    }
    val description = uiText("当前长图缩略图", "Thumbnail of the current capture")
    val loaded = uiText("缩略图已加载", "Thumbnail loaded")
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier.clipToBounds().onSizeChanged { viewport = it }.semantics {
        contentDescription = description
        if (chunks.isNotEmpty()) stateDescription = loaded
    }) {
        val pixelScale = size.width / crop.width.coerceAtLeast(1)
        chunks.forEach { chunk ->
            drawImage(chunk.bitmap.asImageBitmap(),
                dstOffset = IntOffset(0, ((chunk.rect.top - crop.top) * pixelScale).roundToInt()),
                dstSize = IntSize((chunk.rect.width * pixelScale).roundToInt().coerceAtLeast(1), (chunk.rect.height * pixelScale).roundToInt().coerceAtLeast(1)))
        }
        draft.edits.masks.forEach { mask -> mask.intersect(crop)?.let {
            drawRect(Color.Black, Offset((it.left - crop.left) * pixelScale, (it.top - crop.top) * pixelScale),
                Size(it.width * pixelScale, it.height * pixelScale))
        } }
        if (crop.height * pixelScale > size.height) {
            val fade = 28.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, surface), startY = size.height - fade, endY = size.height),
                Offset(0f, size.height - fade), Size(size.width, fade))
        }
    }
}

@Preview(name = "连页", showBackground = true)
@Preview(name = "连页深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewHome() {
    LianyeTheme {
        MainScreen(
            uiState = MainUiState(false, true, false, RestrictedCardPhase.HIDDEN, R.string.vendor_hint_aosp),
            onPrimaryAction = {}, onResumeDraft = {}, onNewCapture = {},
            onHideOverlay = {}, onStopService = {}, onOpenAppDetailsSettings = {},
            onOpenBatteryOptimization = {}, onCopyAdbCommand = {}
        )
    }
}


@Composable
private fun CoordinateField(value: Int, onCommit: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= 7 && input.all { it.isDigit() }) {
                text = input
                input.toIntOrNull()?.let(onCommit)
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(72.dp)
    )
}
