package org.lianye.ui.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.lianye.domain.model.CaptureCompletion
import org.lianye.R
import org.lianye.domain.model.Draft
import org.lianye.domain.model.ImageRect
import org.lianye.engine.export.MediaExportManager
import org.lianye.ui.common.theme.LianyeAccent
import org.lianye.ui.common.uiText
import org.lianye.ui.common.LianyePrimaryButton
import org.lianye.ui.common.LianyeToolAction
import kotlin.math.roundToInt

private enum class EditTool { VIEW, CROP }
private val SelectionSaver = Saver<ImageRect?, IntArray>(
    save = { rect -> rect?.let { intArrayOf(it.left, it.top, it.right, it.bottom) } },
    restore = { if (it.size == 4) ImageRect(it[0], it[1], it[2], it[3]) else null }
)
private val CropHistorySaver = Saver<CropHistory, ArrayList<Int>>(
    save = { state -> arrayListOf(state.index).also { values ->
        state.entries.forEach { rect -> values.addAll(listOf(rect.left, rect.top, rect.right, rect.bottom)) }
    } },
    restore = { values ->
        val entries = values.drop(1).chunked(4).filter { it.size == 4 }.map { ImageRect(it[0], it[1], it[2], it[3]) }
        if (entries.isEmpty()) null else CropHistory(entries, values[0].coerceIn(0, entries.lastIndex))
    }
)

@Composable
@Suppress("UNUSED_PARAMETER")
fun ScrollPreviewScreen(
    draft: Draft,
    onDraftChange: (Draft) -> Unit,
    onSaveClick: (Boolean) -> Unit,
    onShareClick: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExporting: Boolean = false,
    exportProgress: Float = 0f,
    exportMessage: String? = null,
    onOpenGalleryClick: (() -> Unit)? = null,
    onViewportChange: (Float, Float) -> Unit,
) {
    val crop = draft.crop
    var tool by rememberSaveable(draft.id) { mutableStateOf(EditTool.VIEW) }
    var selection by rememberSaveable(draft.id, stateSaver = SelectionSaver) { mutableStateOf<ImageRect?>(null) }
    var history by rememberSaveable(draft.id, stateSaver = CropHistorySaver) { mutableStateOf(CropHistory(listOf(crop))) }
    var offsetY by rememberSaveable(draft.id) { mutableFloatStateOf(maxOf(crop.top.toFloat(), draft.viewOffsetY)) }
    var zoom by rememberSaveable(draft.id) { mutableFloatStateOf(draft.viewScale.coerceIn(.01f, 8f)) }
    var panX by rememberSaveable(draft.id) { mutableFloatStateOf(0f) }
    var cropOffset by rememberSaveable(draft.id) { mutableFloatStateOf(crop.top.toFloat()) }
    var cropOverview by rememberSaveable(draft.id) { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var viewMenu by remember { mutableStateOf(false) }
    val editing = tool == EditTool.CROP
    val bounds = if (editing) draft.bounds else crop
    val density = LocalDensity.current
    val inset = with(density) { if (editing) 24.dp.toPx() else 0f }
    val usableWidth = (viewport.width - inset * 2).coerceAtLeast(1f)
    val usableHeight = (viewport.height - inset * 2).coerceAtLeast(1f)
    val baseScale = usableWidth / bounds.width.coerceAtLeast(1)
    val fullZoom = minOf(1f, usableHeight / bounds.height.coerceAtLeast(1) / baseScale)
    val activeZoom = if (editing) { if (cropOverview) fullZoom else 1f } else zoom
    val scale = baseScale * activeZoom
    val activeOffset = if (editing) { if (cropOverview) bounds.top.toFloat() else cropOffset } else offsetY
    val activePan = if (editing) 0f else panX
    val viewportCallback by rememberUpdatedState(onViewportChange)
    val currentOffset by rememberUpdatedState(offsetY)
    val currentZoom by rememberUpdatedState(zoom)
    // Retain the original API and export identity handling. Very tall images split automatically.
    val split = crop.height > MediaExportManager.PAGINATION_THRESHOLD_PX

    fun cancelCrop() { tool = EditTool.VIEW; selection = null }
    fun startCrop() {
        if (isExporting) return
        history = CropHistory(listOf(crop)); selection = crop
        cropOffset = crop.top.toFloat(); cropOverview = false; tool = EditTool.CROP
    }
    fun commitCrop() {
        val rect = selection ?: history.current
        val edits = draft.edits.copy(crop = rect.takeUnless { it == draft.bounds })
        if (edits != draft.edits) onDraftChange(draft.edit(edits).copy(viewOffsetY = rect.top.toFloat(), viewScale = 1f))
        tool = EditTool.VIEW; selection = null; offsetY = rect.top.toFloat(); zoom = 1f; panX = 0f
    }
    if (!LocalInspectionMode.current) BackHandler {
        if (!isExporting) { if (editing) cancelCrop() else onBackClick() }
    }
    LaunchedEffect(bounds, viewport, scale, editing, cropOverview) {
        if (editing && !cropOverview) cropOffset = PreviewGeometry.clampOffset(cropOffset, bounds, usableHeight, scale)
        else if (editing) Unit
        else {
            offsetY = PreviewGeometry.clampOffset(offsetY, bounds, usableHeight, scale)
            val maxPan = maxOf(0f, (bounds.width * scale - usableWidth) / 2)
            panX = panX.coerceIn(-maxPan, maxPan)
        }
    }
    LaunchedEffect(offsetY, zoom) { delay(250); viewportCallback(offsetY, zoom) }
    DisposableEffect(draft.id) { onDispose { viewportCallback(currentOffset, currentZoom) } }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (editing) TextButton(onClick = ::cancelCrop, enabled = !isExporting) { Text(uiText("取消", "Cancel")) }
                else IconButton(onClick = onBackClick, enabled = !isExporting, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("返回，保留草稿", "Back, keeping the draft"))
                }
                Text(if (editing) uiText("裁剪", "Crop") else uiText("长图", "Long image"),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (editing) Button(onClick = ::commitCrop, enabled = !isExporting, modifier = Modifier.heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.small) {
                    Text(uiText("应用", "Apply"))
                } else Box {
                    TextButton(onClick = { viewMenu = true }, enabled = !isExporting, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("查看", "View"), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(4.dp))
                        Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false },
                        containerColor = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.medium) {
                        fun choose(action: () -> Unit) { viewMenu = false; action() }
                        DropdownMenuItem(text = { Text(uiText("适合宽度", "Fit width"), style = MaterialTheme.typography.bodyMedium) }, onClick = { choose { zoom = 1f; panX = 0f } })
                        DropdownMenuItem(text = { Text(uiText("查看全图", "Show whole image"), style = MaterialTheme.typography.bodyMedium) }, onClick = { choose { zoom = fullZoom; offsetY = crop.top.toFloat(); panX = 0f } })
                        DropdownMenuItem(text = { Text(uiText("放大", "Zoom in"), style = MaterialTheme.typography.bodyMedium) }, onClick = { choose { zoom = (zoom * 1.5f).coerceAtMost(8f) } })
                        DropdownMenuItem(text = { Text(uiText("缩小", "Zoom out"), style = MaterialTheme.typography.bodyMedium) }, onClick = { choose { zoom = (zoom / 1.5f).coerceAtLeast(fullZoom) } })
                        DropdownMenuItem(text = { Text(uiText("顶部", "Top"), style = MaterialTheme.typography.bodyMedium) }, onClick = { choose { offsetY = crop.top.toFloat() } })
                        DropdownMenuItem(text = { Text(uiText("底部", "Bottom"), style = MaterialTheme.typography.bodyMedium) }, onClick = { choose { offsetY = PreviewGeometry.maxOffset(crop, usableHeight, scale) } })
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editing) {
                            Text(uiText("拖动四角裁剪", "Drag the four corners to crop"),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row {
                                    IconButton(onClick = { history = history.undo(); selection = history.current }, enabled = history.canUndo && !isExporting,
                                        modifier = Modifier.size(48.dp)) {
                                        Icon(painterResource(R.drawable.ic_crop_undo), contentDescription = uiText("撤销裁剪", "Undo crop"), modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { history = history.redo(); selection = history.current }, enabled = history.canRedo && !isExporting,
                                        modifier = Modifier.size(48.dp)) {
                                        Icon(painterResource(R.drawable.ic_crop_redo), contentDescription = uiText("重做裁剪", "Redo crop"), modifier = Modifier.size(20.dp))
                                    }
                                }
                                TextButton(onClick = { history = history.change(draft.bounds); selection = history.current }, enabled = !isExporting) { Text(uiText("还原", "Reset")) }
                                TextButton(onClick = { cropOverview = !cropOverview }, enabled = !isExporting) {
                                    Text(if (cropOverview) uiText("放大调整", "Zoom to edit") else uiText("查看全图", "Whole image"))
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                LianyeToolAction(uiText("裁剪", "Crop"), R.drawable.ic_action_crop, ::startCrop, !isExporting)
                                LianyePrimaryButton(onClick = { onSaveClick(split) }, enabled = !isExporting && !draft.isSaved,
                                    modifier = Modifier.weight(1f)) {
                                    if (!isExporting && !draft.isSaved) {
                                        Icon(painterResource(R.drawable.ic_action_save), null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (isExporting) uiText("处理中", "Processing") + " ${(exportProgress.coerceIn(0f, 1f) * 100).roundToInt()}%"
                                        else if (draft.isSaved) uiText("已保存", "Saved") else uiText("保存", "Save"))
                                }
                                LianyeToolAction(uiText("分享", "Share"), R.drawable.ic_action_share, { onShareClick(split) }, !isExporting)
                            }
                            if (isExporting) LinearProgressIndicator(progress = { exportProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            if (split) Text(uiText("这张长图较大，将分为多张图片导出。", "This large capture will be exported as multiple images."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (draft.isSaved && onOpenGalleryClick != null && !isExporting) TextButton(
                                onClick = onOpenGalleryClick, modifier = Modifier.fillMaxWidth()) { Text(uiText("在相册中查看", "View in gallery")) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!editing) {
                Text(if (draft.isSaved) uiText("已保存", "Saved") else uiText("未保存", "Unsaved"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 10.dp))
                completionNotice(draft.completion)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
                }
            }
            PreviewViewport(
                draft = draft, bounds = bounds, editing = editing, selection = selection,
                offsetY = activeOffset, zoom = activeZoom, panX = activePan, viewport = viewport,
                scale = scale, inset = inset, minZoom = fullZoom,
                enabled = !isExporting, onSize = { viewport = it },
                onView = { y, z, x ->
                    if (editing) { if (!cropOverview) cropOffset = y }
                    else { offsetY = y; zoom = z; panX = x }
                },
                onSelection = { selection = it },
                onCommitSelection = { rect -> history = history.change(rect); selection = history.current },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun completionNotice(completion: CaptureCompletion): String? = when (completion) {
    CaptureCompletion.COMPLETED, CaptureCompletion.USER_STOPPED -> null
    CaptureCompletion.INTERRUPTED -> uiText("截图已中断，已保留有效内容。", "Capture interrupted. Valid content was kept.")
    CaptureCompletion.STORAGE_FULL -> uiText("空间不足，已保留有效内容。", "Storage full. Valid content was kept.")
    CaptureCompletion.SECURE_BLOCKED -> uiText("页面限制截图，已保留有效内容。", "This page blocks capture. Valid content was kept.")
    CaptureCompletion.PERMISSION_LOST -> uiText("截图权限失效，已保留有效内容。", "Capture permission ended. Valid content was kept.")
    CaptureCompletion.ALIGNMENT_FAILED -> uiText("未能接上，已保留前段。", "Could not join the next part. The earlier content was kept.")
}

@Composable
private fun PreviewViewport(
    draft: Draft, bounds: ImageRect, editing: Boolean, selection: ImageRect?,
    offsetY: Float, zoom: Float, panX: Float, viewport: IntSize, scale: Float,
    inset: Float, minZoom: Float, enabled: Boolean,
    onSize: (IntSize) -> Unit, onView: (Float, Float, Float) -> Unit,
    onSelection: (ImageRect?) -> Unit, onCommitSelection: (ImageRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val cache = remember(draft.id) { PreviewTileCache() }
    var reload by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val inspection = LocalInspectionMode.current
    // During crop, decode the original image so removed margins can be restored.
    val displayDraft = remember(draft.id, draft.edits, bounds) {
        draft.copy(history = listOf(draft.edits.copy(crop = bounds)), editIndex = 0)
    }
    val contentHeight = (viewport.height - inset * 2).coerceAtLeast(1f)
    val topInset = maxOf(inset, (viewport.height - bounds.height * scale) / 2)
    val originX = (viewport.width - bounds.width * scale) / 2 + panX
    val chunks by produceState<Result<List<PreviewChunk>>?>(null, draft.id, bounds, offsetY, scale, viewport, reload) {
        if (viewport.height == 0 || inspection) return@produceState
        value = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            runCatching { cache.visible(displayDraft, offsetY, offsetY + contentHeight / scale.coerceAtLeast(.0001f), scale) { context.ensureActive() } }
        }
    }
    val latestY by rememberUpdatedState(offsetY)
    val latestZoom by rememberUpdatedState(zoom)
    val latestPan by rememberUpdatedState(panX)
    val latestScale by rememberUpdatedState(scale)
    val latestTopInset by rememberUpdatedState(topInset)
    val latestSelection by rememberUpdatedState(selection)
    val viewCallback by rememberUpdatedState(onView)
    val selectCallback by rememberUpdatedState(onSelection)
    val commitCallback by rememberUpdatedState(onCommitSelection)
    var activeCorner by remember { mutableStateOf<CropCorner?>(null) }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var dragOriginal by remember { mutableStateOf<ImageRect?>(null) }
    // The final pointer event can precede recomposition. Keep its crop synchronously.
    var dragSelection by remember { mutableStateOf<ImageRect?>(null) }
    val background = MaterialTheme.colorScheme.surfaceContainer
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val description = uiText("长图预览，可双指缩放和拖动查看", "Long image. Pinch to zoom and drag to pan.")
    val scrollbarDescription = uiText("快速定位长图位置", "Move through the long image")
    val imageState = when {
        chunks == null -> uiText("图片载入中", "Image loading")
        chunks?.isFailure == true -> uiText("图片读取失败", "Image could not be loaded")
        else -> uiText("图片已加载", "Image loaded")
    }
    fun source(point: Offset, sourceY: Float = latestY): Offset = Offset(
        (point.x - ((viewport.width - bounds.width * latestScale) / 2 + latestPan)) / latestScale + bounds.left,
        sourceY + (point.y - latestTopInset) / latestScale
    )
    fun updateZoom(center: Offset, nextZoom: Float, pan: Offset = Offset.Zero) {
        val sourceAtCenter = source(center)
        val nextScale = (viewport.width - inset * 2) / bounds.width * nextZoom
        val maxPan = maxOf(0f, (bounds.width * nextScale - viewport.width + inset * 2) / 2)
        val nextX = (center.x - (sourceAtCenter.x - bounds.left) * nextScale -
            (viewport.width - bounds.width * nextScale) / 2 + pan.x).coerceIn(-maxPan, maxPan)
        val nextY = PreviewGeometry.clampOffset(
            sourceAtCenter.y - (center.y - maxOf(inset, (viewport.height - bounds.height * nextScale) / 2)) / nextScale - pan.y / nextScale,
            bounds, contentHeight, nextScale
        )
        viewCallback(nextY, nextZoom, nextX)
    }
    fun moveCurrentCorner(corner: CropCorner, point: Offset, sourceY: Float = latestY) {
        val rect = dragSelection ?: latestSelection ?: return
        val position = source(point, sourceY)
        val moved = CropGeometry.moveCorner(rect, corner, position.x, position.y, bounds)
        dragSelection = moved
        selectCallback(moved)
    }
    LaunchedEffect(activeCorner, enabled) {
        if (!enabled || activeCorner == null) return@LaunchedEffect
        while (activeCorner != null) {
            delay(16)
            val point = dragPoint ?: continue
            val edge = minOf(96f, viewport.height / 4f)
            val delta = when {
                point.y < edge -> -24f * (1f - point.y.coerceAtLeast(0f) / edge)
                point.y > viewport.height - edge -> 24f * (1f - (viewport.height - point.y).coerceAtLeast(0f) / edge)
                else -> 0f
            }
            if (delta == 0f) continue
            val nextY = PreviewGeometry.clampOffset(latestY + delta / latestScale, bounds, contentHeight, latestScale)
            viewCallback(nextY, latestZoom, latestPan)
            activeCorner?.let { moveCurrentCorner(it, point, nextY) }
        }
    }
    Box(modifier.background(background).clipToBounds().onSizeChanged(onSize)) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description; stateDescription = imageState }
            .pointerInput(draft.id, bounds, enabled, viewport, editing) {
                if (!enabled || viewport.width == 0) return@pointerInput
                detectTransformGestures { center, pan, gestureZoom, _ ->
                    updateZoom(center, if (editing) latestZoom else (latestZoom * gestureZoom).coerceIn(minZoom.coerceAtLeast(.001f), 8f), pan)
                }
            }
            .pointerInput(draft.id, bounds, enabled, viewport, editing) {
                if (!enabled || editing || viewport.width == 0) return@pointerInput
                detectTapGestures(onDoubleTap = { updateZoom(it, if (latestZoom < 1.5f) 2f else 1f) })
            }
        ) {
            chunks?.getOrNull()?.forEach { chunk ->
                drawImage(chunk.bitmap.asImageBitmap(),
                    dstOffset = IntOffset(
                        (originX + (chunk.rect.left - bounds.left) * scale).roundToInt(),
                        (topInset + (chunk.rect.top - offsetY) * scale).roundToInt()),
                    dstSize = IntSize((chunk.rect.width * scale).roundToInt().coerceAtLeast(1),
                        (((chunk.rect.bottom - offsetY) * scale).roundToInt() - ((chunk.rect.top - offsetY) * scale).roundToInt()).coerceAtLeast(1)))
            }
            fun rect(rect: ImageRect, color: Color, stroke: Boolean = false) {
                if (rect.width <= 0 || rect.height <= 0) return
                val point = Offset(originX + (rect.left - bounds.left) * scale, topInset + (rect.top - offsetY) * scale)
                val dimensions = Size(rect.width * scale, rect.height * scale)
                if (stroke) drawRect(color, point, dimensions, style = Stroke(2.dp.toPx()))
                else drawRect(color, point, dimensions)
            }
            // Existing redactions remain visible; no new redaction UI is provided.
            draft.edits.masks.forEach { mask -> mask.intersect(bounds)?.let { rect(it, Color.Black) } }
            if (editing) selection?.let { selected ->
                val dim = Color.Black.copy(alpha = .45f)
                rect(ImageRect(bounds.left, bounds.top, bounds.right, selected.top), dim)
                rect(ImageRect(bounds.left, selected.bottom, bounds.right, bounds.bottom), dim)
                rect(ImageRect(bounds.left, selected.top, selected.left, selected.bottom), dim)
                rect(ImageRect(selected.right, selected.top, bounds.right, selected.bottom), dim)
                rect(selected, LianyeAccent, true)
            }
        }
        if (editing && selection != null) {
            val density = LocalDensity.current
            val half = with(density) { 22.dp.toPx() }
            val step = maxOf(1f, with(density) { 4.dp.toPx() } / scale)
            val leftLabel = uiText("向左调整", "Move left")
            val rightLabel = uiText("向右调整", "Move right")
            val upLabel = uiText("向上调整", "Move up")
            val downLabel = uiText("向下调整", "Move down")
            CropCorner.entries.forEach { corner ->
                val left = corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT
                val top = corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT
                val x = if (left) selection.left else selection.right
                val y = if (top) selection.top else selection.bottom
                val center = Offset(originX + (x - bounds.left) * scale, topInset + (y - offsetY) * scale)
                val label = when (corner) {
                    CropCorner.TOP_LEFT -> uiText("左上裁剪角", "Top left crop corner")
                    CropCorner.TOP_RIGHT -> uiText("右上裁剪角", "Top right crop corner")
                    CropCorner.BOTTOM_LEFT -> uiText("左下裁剪角", "Bottom left crop corner")
                    CropCorner.BOTTOM_RIGHT -> uiText("右下裁剪角", "Bottom right crop corner")
                }
                fun adjust(dx: Float, dy: Float): Boolean {
                    val current = latestSelection ?: return false
                    val sourceX = if (left) current.left else current.right
                    val sourceY = if (top) current.top else current.bottom
                    val next = CropGeometry.moveCorner(current, corner, sourceX + dx, sourceY + dy, bounds)
                    selectCallback(next); commitCallback(next); return true
                }
                Box(Modifier.offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
                    .size(44.dp).semantics {
                        contentDescription = label
                        customActions = listOf(
                            CustomAccessibilityAction(leftLabel) { adjust(-step, 0f) },
                            CustomAccessibilityAction(rightLabel) { adjust(step, 0f) },
                            CustomAccessibilityAction(upLabel) { adjust(0f, -step) },
                            CustomAccessibilityAction(downLabel) { adjust(0f, step) })
                    }.onKeyEvent {
                        if (!enabled || it.type != KeyEventType.KeyDown) false
                        else when (it.key) {
                            Key.DirectionLeft -> adjust(-step, 0f)
                            Key.DirectionRight -> adjust(step, 0f)
                            Key.DirectionUp -> adjust(0f, -step)
                            Key.DirectionDown -> adjust(0f, step)
                            else -> false
                        }
                    }.focusable(enabled)
                    .pointerInput(corner, enabled, bounds, viewport) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                val current = latestSelection ?: return@detectDragGestures
                                dragOriginal = current; dragSelection = current; activeCorner = corner
                                dragPoint = Offset(
                                    (viewport.width - bounds.width * latestScale) / 2 + latestPan +
                                        ((if (left) current.left else current.right) - bounds.left) * latestScale,
                                    latestTopInset + ((if (top) current.top else current.bottom) - latestY) * latestScale)
                            },
                            onDragCancel = { selectCallback(dragOriginal); activeCorner = null; dragPoint = null; dragOriginal = null; dragSelection = null },
                            onDragEnd = { (dragSelection ?: latestSelection)?.let(commitCallback); activeCorner = null; dragPoint = null; dragOriginal = null; dragSelection = null }
                        ) { change, amount ->
                            change.consume()
                            val point = (dragPoint ?: return@detectDragGestures) + amount
                            dragPoint = point; moveCurrentCorner(corner, point)
                        }
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).background(LianyeAccent, RoundedCornerShape(2.dp)))
                }
            }
        }
        val maxY = PreviewGeometry.maxOffset(bounds, contentHeight, scale)
        if (maxY > bounds.top && !editing) {
            Canvas(Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxSize().semantics {
                contentDescription = scrollbarDescription
                progressBarRangeInfo = ProgressBarRangeInfo(((offsetY - bounds.top) / (maxY - bounds.top)).coerceIn(0f, 1f), 0f..1f)
                if (enabled) setProgress { fraction ->
                    onView(bounds.top + fraction.coerceIn(0f, 1f) * (maxY - bounds.top), zoom, panX); true
                }
            }.pointerInput(draft.id, bounds, viewport, enabled) {
                if (!enabled || viewport.height == 0) return@pointerInput
                fun jump(y: Float) {
                    val maximum = PreviewGeometry.maxOffset(bounds, contentHeight, latestScale)
                    viewCallback(bounds.top + (y / viewport.height).coerceIn(0f, 1f) * (maximum - bounds.top), latestZoom, latestPan)
                }
                detectDragGestures(onDragStart = { jump(it.y) }) { change, _ -> change.consume(); jump(change.position.y) }
            }) {
                val x = size.width - 5.dp.toPx()
                val thumbHeight = (contentHeight / scale / bounds.height * size.height).coerceIn(minOf(size.height, 48.dp.toPx()), size.height)
                val fraction = ((offsetY - bounds.top) / (maxY - bounds.top)).coerceIn(0f, 1f)
                drawRoundRect(primary.copy(alpha = .35f), Offset(x, fraction * (size.height - thumbHeight)),
                    Size(2.dp.toPx(), thumbHeight), CornerRadius(8f))
            }
        }
        if (chunks == null && !inspection) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
        if (chunks?.isFailure == true) Surface(modifier = Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiText("图片暂时无法读取，草稿仍保留", "Could not load the image. Your draft is kept."))
                TextButton(onClick = { reload++ }) { Text(uiText("重试", "Retry")) }
            }
        }
    }
}
