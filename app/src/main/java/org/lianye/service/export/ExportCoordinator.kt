package org.lianye.service.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lianye.domain.model.Draft
import org.lianye.domain.repository.LianyeRepository
import org.lianye.engine.export.MediaExportManager
import org.lianye.engine.export.ExportException
import org.lianye.engine.export.ExportFailure
import org.lianye.ui.common.localized

data class ExportUiState(
    val busy: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val shareUris: List<Uri> = emptyList(),
    val savedUris: List<Uri> = emptyList(),
    val startNewAfterSave: Boolean = false
)

/** Application-scoped operations survive Activity recreation and never hold an Activity. */
class ExportCoordinator(private val context: Context, private val repository: LianyeRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = MediaExportManager(context.contentResolver)
    private val mutableState = MutableStateFlow(ExportUiState())
    val state = mutableState.asStateFlow()

    fun export(draft: Draft, split: Boolean, share: Boolean, startNewAfterSave: Boolean = false) {
        if (mutableState.value.busy) return
        mutableState.value = ExportUiState(busy = true)
        scope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    val report: (Float) -> Unit = { progress -> mutableState.value = mutableState.value.copy(progress = progress.coerceIn(0f, 1f)) }
                    if (share) manager.shareDraft(context, draft, split, report)
                    else manager.saveDraftToGallery(draft, split, report)
                }
                check(uris.isNotEmpty())
                if (!share) withContext(Dispatchers.IO) {
                    repository.markDraftSaved(draft.id, draft.revision, draft.canonicalSnapshot())
                }
                mutableState.value = ExportUiState(
                    progress = 1f,
                    message = if (share) localized("图片已准备好，请选择分享应用", "Images ready. Choose an app to share.")
                        else localized("已保存 ${uris.size} 张图片到相册", "Saved ${uris.size} image(s) to your gallery"),
                    shareUris = if (share) uris else emptyList(),
                    savedUris = if (share) emptyList() else uris,
                    startNewAfterSave = !share && startNewAfterSave
                )
            } catch (error: Exception) {
                val message = when ((error as? ExportException)?.reason) {
                    ExportFailure.STORAGE -> localized("存储空间不足，导出未完成。请清理空间后重试，草稿仍然保留。", "Not enough storage. Free up space and try again. Your draft is kept.")
                    ExportFailure.INVALID_IMAGE -> localized("图片数据暂时无法导出，草稿仍然保留。请返回检查截图后重试。", "Image data could not be exported. Your draft is kept. Review it and try again.")
                    else -> localized("图片写入失败，请检查存储后重试。草稿仍然保留。", "Could not write the image. Check storage and try again. Your draft is kept.")
                }
                mutableState.value = ExportUiState(message = message)
            }
        }
    }

    fun consumeShare(): List<Uri> {
        val uris = mutableState.value.shareUris
        mutableState.value = mutableState.value.copy(shareUris = emptyList())
        return uris
    }
    fun consumeNewCapture() { mutableState.value = mutableState.value.copy(startNewAfterSave = false) }
    fun clearMessage() { if (!mutableState.value.busy) mutableState.value = ExportUiState() }
    fun shareFailed() { mutableState.value = mutableState.value.copy(message = localized("无法打开分享应用，草稿已保留。", "Unable to open a sharing app. Your draft is safe.")) }
}
