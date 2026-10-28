package org.lianye.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lianye.domain.model.Draft
import org.lianye.domain.model.EditSnapshot
import org.lianye.domain.model.ImageRect
import org.lianye.engine.model.TileMetadata
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.OutputStream

class DraftPngExportTest {
    @get:Rule val temporary = TemporaryFolder()
    private val assembler = StreamingPngAssembler()

    @Test
    fun `crop and opaque redaction match original coordinates across tiles`() {
        val draft = Draft("edited", listOf(tile(0, 6, 4, 0), tile(1, 6, 4, 4))).edit(
            EditSnapshot(ImageRect(1, 2, 5, 7), listOf(ImageRect(2, 3, 4, 6)))
        )
        val bytes = ByteArrayOutputStream()
        assembler.assembleDraft(draft, bytes)
        val image = decodeTestPng(bytes.toByteArray())
        assertEquals(4, image.width)
        assertEquals(5, image.height)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val expected = if (draft.edits.masks[0].contains(x + 1, y + 2)) 0xFF000000.toInt()
                else pixel(x + 1, y + 2)
            assertEquals("Pixel $x, $y", expected, image.getRGB(x, y))
        }
    }

    @Test
    fun `thirty thousand pixel segments have every source row exactly once`() {
        val draft = Draft("long", listOf(tile(0, 2, 30_001, 0), tile(1, 2, 6, 30_001))).edit(
            EditSnapshot(ImageRect(0, 1, 2, 30_006), listOf(ImageRect(0, 29_999, 1, 30_003)))
        )
        val outputs = mutableListOf<ByteArrayOutputStream>()
        val progress = mutableListOf<Float>()
        val heights = assembler.assembleDraftWithPagination(draft, 30_000, {
            ByteArrayOutputStream().also(outputs::add)
        }, progress::add)
        assertEquals(listOf(30_000L, 5L), heights)
        var sourceY = 1
        outputs.forEach { bytes ->
            val image = decodeTestPng(bytes.toByteArray())
            assertTrue(image.height <= 30_000)
            for (y in 0 until image.height) {
                assertEquals(pixel(1, sourceY), image.getRGB(1, y))
                val expected = if (sourceY in 29_999..30_002) 0xFF000000.toInt() else pixel(0, sourceY)
                assertEquals(expected, image.getRGB(0, y))
                sourceY++
            }
        }
        assertEquals(30_006, sourceY)
        assertEquals(1f, progress.last(), 0f)
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `exact threshold creates one image and does not add an empty part`() {
        val outputs = mutableListOf<ByteArrayOutputStream>()
        val heights = assembler.assembleDraftWithPagination(
            Draft("boundary", listOf(tile(0, 1, 30_000, 0))), 30_000,
            { ByteArrayOutputStream().also(outputs::add) }
        )
        assertEquals(listOf(30_000L), heights)
        assertEquals(1, outputs.size)
    }

    @Test(expected = EOFException::class)
    fun `truncated tiles fail before reporting success`() {
        val tile = tile(0, 3, 3, 0)
        tile.file.writeBytes(byteArrayOf(0, 0, 0, 0))
        assembler.assembleDraft(Draft("invalid", listOf(tile)), ByteArrayOutputStream())
    }

    @Test
    fun `failed output is closed and does not report complete progress`() {
        var closed = false
        var lastProgress = 0f
        try {
            assembler.assembleDraftWithPagination(Draft("error", listOf(tile(0, 3, 4, 0))), 2, {
                object : OutputStream() {
                    override fun write(value: Int) { throw java.io.IOException("Disk full") }
                    override fun close() { closed = true }
                }
            }) { lastProgress = it }
            throw AssertionError("Expected export to fail")
        } catch (_: java.io.IOException) {
            assertTrue(closed)
            assertTrue(lastProgress < 1f)
        }
    }

    private fun tile(index: Int, width: Int, height: Int, start: Int): TileMetadata {
        val file = File(temporary.root, "${index}_${width}_${height}.raw")
        DataOutputStream(file.outputStream().buffered()).use { output ->
            for (y in start until start + height) for (x in 0 until width) output.writeInt(pixel(x, y))
        }
        return TileMetadata(index, file, width, height, start)
    }

    private fun pixel(x: Int, y: Int): Int = 0xFF000000.toInt() or ((x and 255) shl 16) or (y and 65535)
}
