package org.lianye.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.lianye.data.draft.DraftStore
import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.EditSnapshot
import org.lianye.domain.model.ImageRect
import org.lianye.domain.model.WeavingState
import org.lianye.engine.TileStore
import org.lianye.engine.model.PixelSlice
import org.lianye.engine.model.TileMetadata
import java.io.File

class LianyeRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    private lateinit var repository: LianyeRepository

    @Before
    fun setUp() {
        repository = LianyeRepository()
    }

    @Test
    fun `initial state should be Idle with disconnected service`() {
        assertEquals(WeavingState.Idle(false), repository.weavingState.value)
    }

    @Test
    fun `startWeaving should transition state to Weaving with zero frames`() {
        repository.updateServiceConnected(true)
        repository.startWeaving()
        val state = repository.weavingState.value
        assertTrue(state is WeavingState.Weaving)
        val weaving = state as WeavingState.Weaving
        assertEquals(0, weaving.frameCount)
        assertEquals(0, weaving.currentHeightPx)
    }

    @Test
    fun `updateProgress should update frameCount and height`() {
        repository.startWeaving()
        repository.updateProgress(4, 1500)
        val state = repository.weavingState.value as WeavingState.Weaving
        assertEquals(4, state.frameCount)
        assertEquals(1500, state.currentHeightPx)
    }

    @Test
    fun `finishWeaving should transition to Preview with correct totalHeight`() {
        repository.startWeaving()
        val tile1 = TileMetadata(0, File("t0"), 100, 500, 0)
        val tile2 = TileMetadata(1, File("t1"), 100, 300, 500)

        repository.finishWeaving(listOf(tile1, tile2))

        val state = repository.weavingState.value
        assertTrue(state is WeavingState.Preview)
        val preview = state as WeavingState.Preview
        assertEquals(2, preview.tiles.size)
        assertEquals(800, preview.totalHeightPx)
    }

    @Test
    fun `failWeaving should transition to Error preserving partial tiles`() {
        repository.startWeaving()
        val partialTile = TileMetadata(0, File("p0"), 100, 400, 0)

        repository.failWeaving("Secure blocked", listOf(partialTile))

        val state = repository.weavingState.value
        assertTrue(state is WeavingState.Error)
        val error = state as WeavingState.Error
        assertEquals("Secure blocked", error.message)
        assertEquals(1, error.partialTiles.size)
    }

    @Test
    fun `reset should return to Idle`() {
        repository.updateServiceConnected(true)
        repository.startWeaving()
        repository.reset()
        assertEquals(WeavingState.Idle(true), repository.weavingState.value)
    }

    @Test
    fun `updateProjectionGranted should update isProjectionGranted StateFlow`() {
        repository.updateProjectionGranted(true)
        assertTrue(repository.isProjectionGranted.value)
        repository.updateProjectionGranted(false)
        org.junit.Assert.assertFalse(repository.isProjectionGranted.value)
    }

    @Test
    fun `reset and service disconnect preserve unfinished draft and editing history`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.finishWeaving(capture(store), CaptureCompletion.USER_STOPPED)
        repo.updateDraft(repo.draft.value!!.edit(EditSnapshot(masks = listOf(ImageRect(1, 1, 3, 5)))))
        repo.saveViewport(4f, 2f)
        repo.setOverlayVisible(true)
        repo.updateServiceConnected(false)
        repo.reset()
        assertTrue(repo.draft.value != null)
        org.junit.Assert.assertFalse(repo.isOverlayVisible.value)
        val reopened = LianyeRepository(DraftStore(root))
        assertEquals(repo.draft.value, reopened.draft.value)
        assertTrue(reopened.weavingState.value is WeavingState.Preview)
    }

    @Test
    fun `new capture requires explicit draft discard even after navigation reset`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        assertTrue(repo.startWeaving())
        repo.finishWeaving(capture(store))
        repo.reset()
        org.junit.Assert.assertFalse(repo.startWeaving())
        repo.discardDraft()
        org.junit.Assert.assertNull(repo.draft.value)
        org.junit.Assert.assertNull(store.load())
        assertTrue(repo.startWeaving())
    }

    @Test
    fun `capture checkpoint is recoverable and failure without tiles retains checkpoint`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.checkpointDraft(capture(store))
        assertEquals(CaptureCompletion.INTERRUPTED, LianyeRepository(DraftStore(root)).draft.value!!.completion)
        repo.failWeaving("permission lost", completion = CaptureCompletion.PERMISSION_LOST)
        assertEquals(8, repo.draft.value!!.height)
        assertEquals(CaptureCompletion.PERMISSION_LOST, store.load()!!.completion)
    }

    @Test
    fun `save records exported revision and later edits remain unsaved`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        val exportedRevision = repo.draft.value!!.revision
        repo.updateDraft(repo.draft.value!!.edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7))))
        repo.markDraftSaved(exportedRevision)
        org.junit.Assert.assertFalse(repo.draft.value!!.isSaved)
        repo.markDraftSaved(repo.draft.value!!.revision)
        assertTrue(repo.draft.value!!.isSaved)
    }

    @Test
    fun `undo back to exported content restores saved status`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        repo.markDraftSaved(repo.draft.value!!.revision)
        repo.updateDraft(repo.draft.value!!.edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7))))
        org.junit.Assert.assertFalse(repo.draft.value!!.isSaved)
        repo.updateDraft(repo.draft.value!!.undo())
        assertTrue("Undoing to the actually exported content must restore saved status", repo.draft.value!!.isSaved)
    }

    @Test
    fun `old export marks its content only and undo can return to that saved content`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving(); repo.finishWeaving(capture(store))
        val exported = repo.draft.value!!
        repo.updateDraft(exported.edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7))))
        assertTrue(repo.markDraftSaved(exported.id, exported.revision, exported.canonicalSnapshot()))
        org.junit.Assert.assertFalse(repo.draft.value!!.isSaved)
        repo.updateDraft(repo.draft.value!!.undo())
        assertTrue(repo.draft.value!!.isSaved)
        repo.updateDraft(repo.draft.value!!.redo())
        org.junit.Assert.assertFalse(repo.draft.value!!.isSaved)
    }

    @Test
    fun `export completion from a discarded draft cannot mark a new capture saved`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving(); repo.finishWeaving(capture(store))
        val exported = repo.draft.value!!
        assertTrue(repo.discardDraft())
        repo.startWeaving(); repo.finishWeaving(capture(store))
        org.junit.Assert.assertFalse(repo.markDraftSaved(exported.id, exported.revision, exported.canonicalSnapshot()))
        org.junit.Assert.assertFalse(repo.draft.value!!.isSaved)
        assertEquals(-1, repo.draft.value!!.savedRevision)
    }

    @Test
    fun `newer save survives late older export and stale editor saved fields`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving(); repo.finishWeaving(capture(store))
        val first = repo.draft.value!!.edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7)))
        val second = first.edit(EditSnapshot(crop = ImageRect(1, 2, 5, 8)))
        repo.updateDraft(second)
        assertTrue(repo.markDraftSaved(second.id, second.revision, second.canonicalSnapshot()))
        org.junit.Assert.assertFalse(repo.markDraftSaved(first.id, first.revision, first.canonicalSnapshot()))
        assertTrue(repo.draft.value!!.isSaved)
        val staleEditor = second.edit(EditSnapshot()).copy(savedRevision = -1, savedSnapshot = null)
        assertTrue(repo.adoptDraft(staleEditor))
        assertEquals(second.revision, repo.draft.value!!.savedRevision)
        assertEquals(second.canonicalSnapshot(), repo.draft.value!!.savedSnapshot)
        org.junit.Assert.assertFalse(repo.draft.value!!.isSaved)
        repo.updateDraft(repo.draft.value!!.undo())
        assertTrue(repo.draft.value!!.isSaved)
    }

    @Test
    fun `current revision with a different export snapshot cannot be marked saved`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving(); repo.finishWeaving(capture(store))
        val current = repo.draft.value!!
        org.junit.Assert.assertFalse(repo.markDraftSaved(current.id, current.revision,
            EditSnapshot(crop = ImageRect(0, 1, 4, 7))))
        org.junit.Assert.assertFalse(repo.draft.value!!.isSaved)
    }

    @Test
    fun `storage failure retains latest in memory and clears banner after successful retry`() {
        val root = temporary.newFolder("draft")
        val store = DraftStore(root)
        val repo = LianyeRepository(store)
        val pending = File(root, "draft.pending").apply { mkdir() }
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        assertEquals("draft_storage_failed", repo.lastPersistenceError.value)
        assertEquals(8, repo.draft.value!!.height)
        pending.delete()
        repo.saveViewport(2f, 1f)
        org.junit.Assert.assertNull(repo.lastPersistenceError.value)
        assertEquals(repo.draft.value, store.load())
    }

    @Test
    fun `late edits cannot regress history or saved revision`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        val first = repo.draft.value!!.edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7)))
        val second = first.edit(first.edits.copy(masks = listOf(ImageRect(1, 2, 3, 4))))
        repo.updateDraft(second)
        repo.markDraftSaved(second.revision)
        repo.updateDraft(first)
        assertEquals(second.history, repo.draft.value!!.history)
        assertTrue(repo.draft.value!!.isSaved)
        val undo = second.undo()
        repo.updateDraft(undo)
        assertEquals(second.revision, repo.draft.value!!.savedRevision)
        assertEquals(undo.editIndex, repo.draft.value!!.editIndex)
    }

    @Test
    fun `old editor callbacks after discard cannot crash or alter next capture`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        val previous = repo.draft.value!!
        repo.discardDraft()
        repo.updateDraft(previous.edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7))))
        org.junit.Assert.assertNull(repo.draft.value)
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        repo.saveViewport(7f, 4f, draftId = previous.id)
        assertEquals(0f, repo.draft.value!!.viewOffsetY)
        assertEquals(1f, repo.draft.value!!.viewScale)
    }

    @Test
    fun `memory adoption immediately exposes redaction while persistence writes latest edit and viewport`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        val original = repo.draft.value!!
        val mask = ImageRect(1, 2, 3, 5)
        val edited = original.edit(EditSnapshot(masks = listOf(mask)))

        assertTrue(repo.adoptDraft(edited))
        // Export can take repository.draft immediately, before any queued disk work runs.
        assertEquals(listOf(mask), repo.draft.value!!.edits.masks)
        assertTrue(store.load()!!.edits.masks.isEmpty())
        assertTrue(repo.adoptViewport(edited.id, 3f, 2f))
        org.junit.Assert.assertFalse(repo.adoptDraft(original))
        repo.persistCurrentDraft()

        assertEquals(repo.draft.value, store.load())
        assertEquals(3f, repo.draft.value!!.viewOffsetY)
        assertEquals(2f, repo.draft.value!!.viewScale)
    }

    @Test
    fun `queued persistence after explicit discard cannot resurrect previous draft`() {
        val store = DraftStore(temporary.newFolder("draft"))
        val repo = LianyeRepository(store)
        repo.startWeaving()
        repo.finishWeaving(capture(store))
        val original = repo.draft.value!!
        assertTrue(repo.adoptDraft(original.edit(EditSnapshot(crop = ImageRect(0, 1, 4, 7)))))
        assertTrue(repo.discardDraft())
        repo.persistCurrentDraft()
        org.junit.Assert.assertFalse(repo.adoptViewport(original.id, 4f, 2f))
        org.junit.Assert.assertNull(repo.draft.value)
        org.junit.Assert.assertNull(store.load())
    }

    private fun capture(store: DraftStore): List<TileMetadata> {
        val tiles = TileStore(store.tilesDirectory, chunkTargetHeight = 4)
        tiles.appendStrip(PixelSlice(IntArray(32 * 8) { -1 }, 32, 8))
        return tiles.finalizeTiles()
    }
}
