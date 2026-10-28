package org.lianye.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lianye.engine.model.PixelSlice
import java.io.File

class TileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tileStore: TileStore
    private val width = 100

    @Before
    fun setUp() {
        tileStore = TileStore(
            storageDir = tempFolder.root,
            chunkTargetHeight = 500
        )
    }

    @Test
    fun `appending strips below chunk height should write to a single tile file`() {
        val strip1 = createStrip(width, 200, 0xFF112233.toInt())
        val strip2 = createStrip(width, 250, 0xFF445566.toInt())

        tileStore.appendStrip(strip1)
        tileStore.appendStrip(strip2)

        val tiles = tileStore.finalizeTiles()
        assertEquals("Should produce 1 tile", 1, tiles.size)
        assertEquals("Tile height should be sum of strips", 450, tiles[0].height)
        assertEquals("Tile width should match", width, tiles[0].width)
        assertEquals("Tile startY should be 0", 0, tiles[0].startY)
        assertTrue("Tile file must exist", tiles[0].file.exists())
        assertEquals("File size must match raw ARGB bytes", (450 * width * 4).toLong(), tiles[0].file.length())
    }

    @Test
    fun `accumulating strips across chunkTargetHeight should split into multiple tiles`() {
        val strip1 = createStrip(width, 400, 0xFF111111.toInt())
        val strip2 = createStrip(width, 300, 0xFF222222.toInt()) // 400 + 300 = 700 > 500

        tileStore.appendStrip(strip1)
        tileStore.appendStrip(strip2)

        val tiles = tileStore.finalizeTiles()
        assertEquals("Should split into 2 tiles", 2, tiles.size)
        assertEquals("First tile should reach chunk target 500px", 500, tiles[0].height)
        assertEquals("Second tile should be remaining 200px", 200, tiles[1].height)
        assertEquals("First tile startY", 0, tiles[0].startY)
        assertEquals("Second tile startY", 500, tiles[1].startY)
        assertTrue("Tile 1 file exists", tiles[0].file.exists())
        assertTrue("Tile 2 file exists", tiles[1].file.exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `strip width mismatch should throw IllegalArgumentException`() {
        val strip1 = createStrip(width, 100, 0xFF111111.toInt())
        val strip2 = createStrip(width + 10, 100, 0xFF222222.toInt())

        tileStore.appendStrip(strip1)
        tileStore.appendStrip(strip2)
    }

    @Test
    fun `clear should delete all physical files and reset metadata`() {
        val strip = createStrip(width, 200, 0xFF333333.toInt())
        tileStore.appendStrip(strip)
        val tiles = tileStore.finalizeTiles()
        val tileFile = tiles[0].file
        assertTrue("File exists before clear", tileFile.exists())

        tileStore.clear()

        assertFalse("File must be deleted after clear", tileFile.exists())
        assertTrue("Metadata must be empty after clear", tileStore.finalizeTiles().isEmpty())
    }

    @Test
    fun `zero deltaY strip with zero height should be ignored`() {
        val emptyStrip = PixelSlice(IntArray(0), width, 0)
        tileStore.appendStrip(emptyStrip)
        val tiles = tileStore.finalizeTiles()
        assertTrue("Empty strip should not create tile", tiles.isEmpty())
    }

    @Test
    fun `constructing new store preserves prior capture and clear only removes owned files`() {
        tileStore.appendStrip(createStrip(width, 20, -1))
        val recoverable = tileStore.finalizeTiles().single().file
        val otherFile = File(tempFolder.root, "tile_unrelated.bin").apply { writeText("keep") }
        val fresh = TileStore(tempFolder.root, chunkTargetHeight = 500)
        fresh.appendStrip(createStrip(width, 10, -1))
        val freshFile = fresh.finalizeTiles().single().file
        assertTrue(recoverable.exists())
        assertTrue(otherFile.exists())
        assertFalse(recoverable == freshFile)
        fresh.clear()
        assertTrue(recoverable.exists())
        assertTrue(otherFile.exists())
        assertFalse(freshFile.exists())
    }

    @Test
    fun `checkpoint contains only complete flushed tiles including final partial tile`() {
        val checkpoints = mutableListOf<List<org.lianye.engine.model.TileMetadata>>()
        val store = TileStore(tempFolder.root, chunkTargetHeight = 5, onCheckpoint = { committed ->
            committed.forEach { assertEquals(it.width.toLong() * it.height * 4, it.file.length()) }
            checkpoints.add(committed)
        })
        store.appendStrip(createStrip(width, 12, -1))
        assertEquals(listOf(5, 10), checkpoints.map { snapshot -> snapshot.sumOf { it.height } })
        assertEquals(12, store.currentHeightPx)
        store.finalizeTiles()
        assertEquals(listOf(5, 10, 12), checkpoints.map { snapshot -> snapshot.sumOf { it.height } })
        assertEquals(12, store.committedTiles().sumOf { it.height })
    }

    @Test
    fun `checkpoint failure preserves committed pixels for salvage`() {
        val store = TileStore(tempFolder.root, chunkTargetHeight = 5, onCheckpoint = { throw java.io.IOException("full") })
        org.junit.Assert.assertThrows(java.io.IOException::class.java) { store.appendStrip(createStrip(width, 8, -1)) }
        val salvaged = store.salvageTiles()
        assertEquals(5, salvaged.sumOf { it.height })
        assertTrue(salvaged.single().file.exists())
    }

    private fun createStrip(w: Int, h: Int, color: Int): PixelSlice {
        val pixels = IntArray(w * h) { color }
        return PixelSlice(pixels, w, h)
    }
}
