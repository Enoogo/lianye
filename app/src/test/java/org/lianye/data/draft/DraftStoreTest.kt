package org.lianye.data.draft

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.Draft
import org.lianye.domain.model.EditSnapshot
import org.lianye.domain.model.ImageRect
import org.lianye.engine.TileStore
import org.lianye.engine.model.PixelSlice
import org.lianye.engine.model.TileMetadata
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

class DraftStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `restart restores image edits undo redo and viewport`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val tiles = capture(store)
        val original = Draft("capture-1", tiles, completion = CaptureCompletion.USER_STOPPED)
            .edit(EditSnapshot(crop = ImageRect(0, 2, 4, 7)))
            .edit(EditSnapshot(crop = ImageRect(0, 2, 4, 7), masks = listOf(ImageRect(1, 3, 3, 5))))
            .undo().copy(viewOffsetY = 4f, viewScale = 2.5f)
        store.save(original)
        val restored = DraftStore(root).load()!!

        assertEquals(original, restored)
        assertTrue(restored.canUndo)
        assertTrue(restored.canRedo)
        assertEquals(ImageRect(1, 3, 3, 5), restored.redo().edits.masks.single())
        assertTrue(tiles.all { it.file.exists() })
    }

    @Test
    fun `corrupt primary recovers last valid manifest without deleting pixels`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val draft = Draft("capture-1", capture(store))
        store.save(draft)
        store.save(draft.copy(viewOffsetY = 3f))
        File(root, "draft.bin").writeBytes(byteArrayOf(1, 2, 3))

        assertEquals(draft, DraftStore(root).load())
        assertTrue(draft.tiles.all { it.file.exists() })
    }

    @Test
    fun `uncommitted pending metadata does not replace committed draft`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val draft = Draft("capture-1", capture(store))
        store.save(draft)
        File(root, "draft.pending").writeText("unfinished")
        assertEquals(draft, DraftStore(root).load())
    }

    @Test
    fun `truncated tile cannot be restored or saved as a complete draft`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val draft = Draft("capture-1", capture(store))
        store.save(draft)
        draft.tiles.first().file.writeBytes(byteArrayOf(1))
        assertNull(DraftStore(root).load())
        assertThrows(IllegalArgumentException::class.java) { store.save(draft) }
    }

    @Test
    fun `outside tile path is rejected and explicit clear stays in draft directory`() {
        val root = temporary.newFolder("draft")
        val outside = temporary.newFile("important.bin").apply { writeBytes(ByteArray(4 * 4 * 8)) }
        val store = DraftStore(root)
        assertThrows(IllegalArgumentException::class.java) {
            store.save(Draft("capture-1", listOf(TileMetadata(0, outside, 4, 8, 0))))
        }
        val draft = Draft("capture-1", capture(store))
        store.save(draft)
        store.clear()
        assertNull(store.load())
        assertTrue(outside.exists())
        assertTrue(draft.tiles.none { it.file.exists() })
    }

    @Test
    fun `every tile checkpoint can be reopened before capture ends`() {
        val store = DraftStore(temporary.newFolder("draft"))
        var checkpointCount = 0
        val tiles = TileStore(store.tilesDirectory, chunkTargetHeight = 4, onCheckpoint = { committed ->
            store.save(Draft("capture-1", committed, completion = CaptureCompletion.INTERRUPTED))
            assertEquals(committed, DraftStore(store.tilesDirectory.parentFile!!).load()!!.tiles)
            checkpointCount++
        })
        tiles.appendStrip(PixelSlice(IntArray(32 * 9) { -1 }, 32, 9))
        assertEquals(2, checkpointCount)
        assertEquals(8, store.load()!!.height)
        tiles.finalizeTiles()
        assertEquals(3, checkpointCount)
        assertEquals(9, store.load()!!.height)
    }

    @Test
    fun `saved output snapshot survives restart and undo redo after a later crop`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val cropped = Draft("capture-1", capture(store)).edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7)))
        val saved = cropped.copy(savedRevision = cropped.revision, savedSnapshot = cropped.canonicalSnapshot())
        val changed = saved.edit(EditSnapshot(crop = ImageRect(1, 2, 5, 8)))
        store.save(changed)
        val restored = DraftStore(root).load()!!
        assertEquals(changed, restored)
        assertFalse(restored.isSaved)
        assertTrue(restored.undo().isSaved)
        assertFalse(restored.undo().redo().isSaved)
        store.save(restored.undo())
        assertTrue(DraftStore(root).load()!!.isSaved)
    }

    @Test
    fun `v1 saved metadata migrates current output snapshot and preserves old masks`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val edited = Draft("capture-v1", capture(store)).edit(EditSnapshot(
            crop = ImageRect(0, 1, 4, 7), masks = listOf(ImageRect(1, 2, 3, 5))))
        val old = edited.copy(savedRevision = edited.revision)
        writeV1(root, old)
        val restored = DraftStore(root).load()!!
        assertEquals(old.history, restored.history)
        assertEquals(old.edits.masks, restored.edits.masks)
        assertEquals(old.canonicalSnapshot(), restored.savedSnapshot)
        assertTrue(restored.isSaved)
        assertTrue(restored.edit(EditSnapshot(crop = ImageRect(0, 0, 4, 8))).undo().isSaved)
        store.save(restored)
        assertEquals(restored, DraftStore(root).load())
    }

    @Test
    fun `v1 older saved revision keeps masks without guessing an unknown exported snapshot`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val old = Draft("capture-v1", capture(store))
            .edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7)))
            .edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7), masks = listOf(ImageRect(1, 2, 3, 5))))
            .undo().copy(savedRevision = 1)
        writeV1(root, old)
        val restored = DraftStore(root).load()!!
        assertEquals(old, restored)
        assertNull(restored.savedSnapshot)
        assertFalse(restored.isSaved)
        assertEquals(old.history.last().masks, restored.redo().edits.masks)
    }

    private fun writeV1(root: File, draft: Draft) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            fun rect(rect: ImageRect) {
                out.writeInt(rect.left); out.writeInt(rect.top); out.writeInt(rect.right); out.writeInt(rect.bottom)
            }
            out.writeUTF(draft.id)
            out.writeInt(draft.tiles.size)
            draft.tiles.forEach { tile ->
                out.writeInt(tile.index)
                out.writeUTF(tile.file.relativeTo(File(root, "tiles")).path.replace('\\', '/'))
                out.writeInt(tile.width); out.writeInt(tile.height); out.writeInt(tile.startY)
            }
            out.writeInt(draft.history.size)
            draft.history.forEach { edit ->
                out.writeBoolean(edit.crop != null); edit.crop?.let { rect(it) }
                out.writeInt(edit.masks.size); edit.masks.forEach { rect(it) }
            }
            out.writeInt(draft.editIndex); out.writeInt(draft.revision); out.writeInt(draft.savedRevision)
            out.writeFloat(draft.viewOffsetY); out.writeFloat(draft.viewScale)
            out.writeUTF(draft.completion.name)
        }
        val payload = buffer.toByteArray()
        DataOutputStream(File(root, "draft.bin").outputStream()).use { out ->
            out.writeInt(0x4C4F4F4D); out.writeInt(1); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        }
    }

    private fun capture(store: DraftStore): List<TileMetadata> {
        val tileStore = TileStore(store.tilesDirectory, chunkTargetHeight = 4)
        tileStore.appendStrip(PixelSlice(IntArray(32 * 8) { -1 }, 32, 8))
        return tileStore.finalizeTiles()
    }
}
