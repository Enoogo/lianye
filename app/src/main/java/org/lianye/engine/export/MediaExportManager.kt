package org.lianye.engine.export

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.lianye.domain.model.Draft
import org.lianye.engine.StreamingPngAssembler
import org.lianye.engine.model.TileMetadata
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

enum class ExportFailure { INVALID_IMAGE, STORAGE, WRITE }

class ExportException(val reason: ExportFailure, cause: Throwable) : IOException(cause.message, cause)

class MediaExportManager(
    private val contentResolver: ContentResolver,
    private val assembler: StreamingPngAssembler = StreamingPngAssembler()
) {
    companion object {
        /**
         * Single-image ceiling. PNG stores width/height as unsigned 32-bit integers, but
         * Android's Bitmap/PNG decoders use a signed 32-bit int, so 2^31-1 rows is both the
         * format limit and the largest value any Android consumer can even parse.
         * Export is streamed tile-by-tile, so no bitmap ever has to hold the whole image:
         * the practical ceiling is free storage, not memory.
         */
        const val PAGINATION_THRESHOLD_PX = Int.MAX_VALUE
        private const val SHARE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }

    fun saveTilesToGallery(
        tiles: List<TileMetadata>,
        title: String = "Lianye_${System.currentTimeMillis()}"
    ): Uri? = saveAllTilesToGallery(tiles, title).firstOrNull()

    fun saveAllTilesToGallery(
        tiles: List<TileMetadata>,
        title: String = "Lianye_${System.currentTimeMillis()}"
    ): List<Uri> {
        if (tiles.isEmpty()) return emptyList()
        return save(Draft("export", tiles), true, title) {}
    }

    /** Pending items are published only after every part is written; failures roll all of them back. */
    fun saveDraftToGallery(
        draft: Draft,
        split: Boolean,
        onProgress: (Float) -> Unit = {}
    ): List<Uri> = save(draft, split, "Lianye_${System.currentTimeMillis()}", onProgress)

    private fun save(draft: Draft, split: Boolean, title: String, onProgress: (Float) -> Unit): List<Uri> {
        try {
            val count = partCount(draft, split)
            val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            val inserted = ExportTransaction.write(create = { index ->
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, partName(title, index, count))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lianye")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create image in gallery")
            }, open = { uri -> contentResolver.openOutputStream(uri, "w") }, writeSegments = { factory ->
                assembler.assembleDraftWithPagination(draft, segmentHeight(draft, split), factory) { onProgress(it * 0.98f) }
            }, publish = { uri -> contentResolver.update(uri, ready, null, null) > 0 },
                rollback = { uri -> contentResolver.delete(uri, null, null); Unit })
            onProgress(1f)
            return inserted
        } catch (error: Exception) {
            throw exportError(error)
        }
    }

    /** Uses private temporary files, never gallery records. Keep the files for the receiving app. */
    fun shareDraft(
        context: Context,
        draft: Draft,
        split: Boolean,
        onProgress: (Float) -> Unit = {}
    ): List<Uri> {
        val created = mutableListOf<File>()
        try {
            val shareDirectory = File(context.cacheDir, "share")
            if (!shareDirectory.isDirectory && !shareDirectory.mkdirs()) {
                throw IOException("Could not create sharing directory")
            }
            val cutoff = System.currentTimeMillis() - SHARE_MAX_AGE_MS
            shareDirectory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
            val title = "Lianye_${UUID.randomUUID()}"
            val count = partCount(draft, split)
            assembler.assembleDraftWithPagination(draft, segmentHeight(draft, split), { index ->
                val file = File(shareDirectory, partName(title, index, count))
                created.add(file)
                FileOutputStream(file)
            }, onProgress)
            return created.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        } catch (error: Exception) {
            created.forEach { it.delete() }
            throw exportError(error)
        }
    }

    fun createShareIntent(uri: Uri): Intent = createShareIntent(listOf(uri))

    fun createShareIntent(uris: List<Uri>): Intent {
        require(uris.isNotEmpty()) { "There are no images to share" }
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newUri(contentResolver, "Lianye", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun segmentHeight(draft: Draft, split: Boolean) =
        if (split) PAGINATION_THRESHOLD_PX else draft.crop.height.coerceAtLeast(1)

    private fun partCount(draft: Draft, split: Boolean): Int {
        require(draft.crop.height > 0) { "Draft has no image" }
        val height = segmentHeight(draft, split)
        return ((draft.crop.height.toLong() + height - 1) / height).toInt()
    }

    private fun partName(title: String, index: Int, count: Int) =
        if (count == 1) "$title.png" else "${title}_part${(index + 1).toString().padStart(count.toString().length, '0')}.png"

    private fun exportError(error: Exception): ExportException {
        if (error is ExportException) return error
        val reason = when {
            error is IllegalArgumentException -> ExportFailure.INVALID_IMAGE
            listOf("space", "ENOSPC", "disk full").any { error.message?.contains(it, ignoreCase = true) == true } -> ExportFailure.STORAGE
            else -> ExportFailure.WRITE
        }
        return ExportException(reason, error)
    }
}
