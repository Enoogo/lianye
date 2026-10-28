package org.lianye.engine.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lianye.domain.model.Draft
import org.lianye.domain.model.EditSnapshot
import org.lianye.domain.model.ImageRect
import org.lianye.engine.model.TileMetadata
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/** Real MediaStore and FileProvider regressions. Cleanup targets only files/URIs created here. */
class MediaExportManagerPlatformTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver

    @Test
    fun gallerySplitHasTwoReadableImagesAndEverySourceRowInOrder() {
        val source = createDraft(width = 48, heights = listOf(20_000, 10_001))
        val created = mutableListOf<Uri>()
        try {
            val progress = mutableListOf<Float>()
            created.addAll(MediaExportManager(resolver).saveDraftToGallery(source, split = true, onProgress = progress::add))
            assertEquals(2, created.size)
            assertEquals(1f, progress.last(), 0f)
            assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
            var sourceY = 0
            created.forEachIndexed { index, uri ->
                assertEquals("image/png", resolver.getType(uri))
                resolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
                decode(uri).useBitmap { image ->
                    assertEquals(48, image.width)
                    assertEquals(if (index == 0) 30_000 else 1, image.height)
                    checkPixels(image, source, sourceY)
                    sourceY += image.height
                }
            }
            assertEquals(30_001, sourceY)
        } finally {
            created.forEach { resolver.delete(it, null, null) }
            source.tiles.forEach { it.file.delete() }
        }
    }

    @Test
    fun fileProviderSharesAllSplitPartsAndDoesNotInsertGalleryRows() {
        val original = createDraft(width = 48, heights = listOf(19_000, 11_009))
        val edited = original.edit(EditSnapshot(
            crop = ImageRect(4, 3, 44, 30_004),
            masks = listOf(ImageRect(8, 29_997, 19, 30_004))
        ))
        val created = mutableListOf<Uri>()
        try {
            val galleryBefore = ownGalleryRows()
            val manager = MediaExportManager(resolver)
            created.addAll(manager.shareDraft(context, edited, split = true))
            assertEquals(2, created.size)
            assertEquals(galleryBefore, ownGalleryRows())
            var sourceY = edited.crop.top
            created.forEachIndexed { index, uri ->
                assertEquals("${context.packageName}.files", uri.authority)
                assertEquals("image/png", resolver.getType(uri))
                decode(uri).useBitmap { image ->
                    assertEquals(40, image.width)
                    assertEquals(if (index == 0) 30_000 else 1, image.height)
                    checkPixels(image, edited, sourceY)
                    sourceY += image.height
                }
            }
            assertEquals(edited.crop.bottom, sourceY)
            val intent = manager.createShareIntent(created)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
            assertEquals("image/png", intent.type)
            @Suppress("DEPRECATION")
            val streams = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            assertEquals(created, streams)
            assertEquals(created.size, intent.clipData!!.itemCount)
            created.forEachIndexed { index, uri -> assertEquals(uri, intent.clipData!!.getItemAt(index).uri) }
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            // Preparing the intent leaves files readable for the receiving app.
            created.forEach { uri -> resolver.openInputStream(uri)!!.use { assertEquals(0x89, it.read()) } }
            assertEquals(galleryBefore, ownGalleryRows())
        } finally {
            created.forEach { resolver.delete(it, null, null) }
            original.tiles.forEach { it.file.delete() }
        }
    }

    @Test
    fun galleryCropAndOpaqueRedactionMatchSourcePixelsAcrossTileBoundary() {
        val original = createDraft(width = 48, heights = listOf(6, 7))
        val edited = original.edit(EditSnapshot(ImageRect(5, 3, 42, 11), listOf(ImageRect(8, 5, 22, 9))))
        val created = mutableListOf<Uri>()
        try {
            created.addAll(MediaExportManager(resolver).saveDraftToGallery(edited, split = false))
            assertEquals(1, created.size)
            decode(created.single()).useBitmap { image ->
                assertEquals(37, image.width)
                assertEquals(8, image.height)
                checkPixels(image, edited, edited.crop.top)
                assertEquals(0xFF000000.toInt(), image.getPixel(8 - edited.crop.left, 5 - edited.crop.top))
            }
        } finally {
            created.forEach { resolver.delete(it, null, null) }
            original.tiles.forEach { it.file.delete() }
        }
    }

    private fun createDraft(width: Int, heights: List<Int>): Draft {
        val id = "platform-export-${UUID.randomUUID()}"
        var startY = 0
        val tiles = heights.mapIndexed { index, height ->
            val file = File(context.cacheDir, "${id}_$index.raw")
            DataOutputStream(file.outputStream().buffered()).use { out ->
                for (y in startY until startY + height) for (x in 0 until width) out.writeInt(pixel(x, y))
            }
            TileMetadata(index, file, width, height, startY).also { startY += height }
        }
        return Draft(id, tiles)
    }

    private fun decode(uri: Uri): Bitmap {
        val bitmap = resolver.openInputStream(uri)!!.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        }
        assertNotNull("Exported URI must decode: $uri", bitmap)
        return bitmap!!
    }

    private fun checkPixels(image: Bitmap, draft: Draft, firstY: Int) {
        val row = IntArray(image.width)
        for (y in 0 until image.height) {
            image.getPixels(row, 0, image.width, 0, y, image.width, 1)
            for (x in row.indices) {
                val sourceX = draft.crop.left + x
                val sourceY = firstY + y
                val expected = if (draft.edits.masks.any { it.contains(sourceX, sourceY) }) 0xFF000000.toInt()
                    else pixel(sourceX, sourceY)
                assertEquals("Source pixel $sourceX,$sourceY", expected, row[x])
            }
        }
    }

    private fun pixel(x: Int, y: Int) = 0xFF000000.toInt() or ((x * 5 and 255) shl 16) or (y and 65_535)

    private fun ownGalleryRows(): Set<Long> {
        val rows = mutableSetOf<Long>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?", arrayOf(context.packageName), null
        )!!.use { cursor -> while (cursor.moveToNext()) rows.add(cursor.getLong(0)) }
        return rows
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
        try { block(this) } finally { recycle() }
    }
}
