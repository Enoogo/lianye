package org.lianye.ui.preview

import android.graphics.Bitmap
import org.lianye.domain.model.Draft
import org.lianye.domain.model.ImageRect
import java.io.RandomAccessFile
import kotlin.math.ceil
import kotlin.math.floor

internal data class PreviewChunk(val rect: ImageRect, val bitmap: Bitmap)

/** Decodes bounded strips, never an entire long image. ARGB bytes match TileStore's wire format. */
internal class PreviewTileCache(private val budget: Int = 32 * 1024 * 1024) {
    private data class Key(val file: String, val top: Int, val bottom: Int, val left: Int, val right: Int, val sample: Int)
    private val cache = LinkedHashMap<Key, Bitmap>(16, 0.75f, true)
    private var bytes = 0

    @Synchronized
    fun visible(draft: Draft, top: Float, bottom: Float, pixelScale: Float, checkCancelled: () -> Unit = {}): List<PreviewChunk> {
        require(pixelScale.isFinite() && pixelScale > 0f) { "Preview scale must be finite and positive" }
        checkCancelled()
        val crop = draft.crop
        var sample = 1
        val sourceHeight = maxOf(0f, bottom - top)
        while (sample * pixelScale < 0.75f ||
            crop.width.toLong() / sample * (512 / sample + 1) * 4 > budget / 4 ||
            crop.width.toDouble() / sample * (sourceHeight / sample + 1) * 4 > budget / 2) {
            sample *= 2
        }
        val visibleTop = floor(top).toInt().coerceAtLeast(crop.top)
        val visibleBottom = ceil(bottom).toInt().coerceAtMost(crop.bottom)
        val result = mutableListOf<PreviewChunk>()
        for (tile in draft.tiles) {
            if (tile.startY >= visibleBottom || tile.startY + tile.height <= visibleTop) continue
            var chunkTop = tile.startY + ((maxOf(visibleTop, tile.startY) - tile.startY) / 512) * 512
            while (chunkTop < minOf(tile.startY + tile.height, visibleBottom)) {
                checkCancelled()
                val chunkBottom = minOf(tile.startY + tile.height, chunkTop + 512, crop.bottom)
                val start = maxOf(chunkTop, crop.top)
                val key = Key(tile.file.absolutePath, start, chunkBottom, crop.left, crop.right, sample)
                val bitmap = cache[key] ?: decode(tile.file.absolutePath, tile.width, tile.startY, key, checkCancelled).also {
                    while (bytes + it.allocationByteCount > budget && cache.isNotEmpty()) {
                        val oldest = cache.entries.iterator()
                        bytes -= oldest.next().value.allocationByteCount
                        oldest.remove()
                    }
                    cache[key] = it
                    bytes += it.allocationByteCount
                }
                result.add(PreviewChunk(ImageRect(crop.left, start, crop.right, chunkBottom), bitmap))
                chunkTop += 512
            }
        }
        return result
    }

    private fun decode(path: String, sourceWidth: Int, tileTop: Int, key: Key, checkCancelled: () -> Unit): Bitmap {
        val width = (key.right - key.left + key.sample - 1) / key.sample
        val height = (key.bottom - key.top + key.sample - 1) / key.sample
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val row = ByteArray((key.right - key.left) * 4)
            val pixels = IntArray(width)
            RandomAccessFile(path, "r").use { file ->
                for (y in 0 until height) {
                    checkCancelled()
                    val sourceY = minOf(key.bottom - 1, key.top + y * key.sample)
                    file.seek(((sourceY - tileTop).toLong() * sourceWidth + key.left) * 4)
                    file.readFully(row)
                    for (x in 0 until width) {
                        val offset = x * key.sample * 4
                        pixels[x] = ((row[offset].toInt() and 255) shl 24) or
                            ((row[offset + 1].toInt() and 255) shl 16) or
                            ((row[offset + 2].toInt() and 255) shl 8) or (row[offset + 3].toInt() and 255)
                    }
                    bitmap.setPixels(pixels, 0, width, 0, y, width, 1)
                }
            }
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }
}
