package org.lianye.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lianye.engine.model.TileMetadata
import java.io.File

class DraftTest {
    private fun draft() = Draft("draft", listOf(TileMetadata(0, File("tile"), 32, 100, 0)))

    @Test fun `undo and redo saved content depend on output rather than edit revision`() {
        val original = draft()
        val saved = original.copy(savedRevision = 0, savedSnapshot = original.canonicalSnapshot())
        val cropped = saved.edit(EditSnapshot(ImageRect(2, 10, 30, 90)))
        assertFalse(cropped.isSaved)
        val undone = cropped.undo()
        assertTrue(undone.isSaved)
        assertTrue(undone.revision > saved.savedRevision)
        assertFalse(undone.redo().isSaved)
    }

    @Test fun `explicit full crop and reordered masks normalize to the same saved output`() {
        val original = draft()
        val firstMask = ImageRect(1, 2, 4, 8)
        val secondMask = ImageRect(10, 15, 20, 25)
        val saved = original.edit(EditSnapshot(masks = listOf(firstMask, secondMask)))
        val marked = saved.copy(savedRevision = saved.revision, savedSnapshot = saved.canonicalSnapshot())
        val sameOutput = marked.edit(EditSnapshot(original.bounds, listOf(secondMask, firstMask, firstMask)))
        assertTrue(sameOutput.isSaved)
        assertEquals(marked.canonicalSnapshot(), sameOutput.canonicalSnapshot())
    }

    @Test fun `masks outside crop do not change exported output while old mask history stays intact`() {
        val original = draft()
        val crop = ImageRect(5, 10, 25, 90)
        val cropped = original.edit(EditSnapshot(crop))
        val saved = cropped.copy(savedRevision = cropped.revision, savedSnapshot = cropped.canonicalSnapshot())
        val outsideMask = ImageRect(0, 0, 3, 8)
        val edited = saved.edit(EditSnapshot(crop, listOf(outsideMask)))
        assertTrue(edited.isSaved)
        assertEquals(listOf(outsideMask), edited.edits.masks)
    }

    @Test fun `legacy current saved revision acquires a snapshot before the next edit`() {
        val saved = draft().copy(savedRevision = 0)
        val cropped = saved.edit(EditSnapshot(ImageRect(2, 10, 30, 90)))
        assertFalse(cropped.isSaved)
        assertTrue(cropped.undo().isSaved)
    }
}
