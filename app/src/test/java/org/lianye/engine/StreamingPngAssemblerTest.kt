package org.lianye.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lianye.engine.model.TileMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

class StreamingPngAssemblerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var assembler: StreamingPngAssembler
    private val width = 80
    private val heightPerTile = 60

    @Before
    fun setUp() {
        assembler = StreamingPngAssembler()
    }

    @Test
    fun `assemble single tile should produce valid PNG with exact colors verified via decodePng`() {
        // Red color in ARGB: 0xFFFF0000 -> R=255, G=0, B=0, A=255
        val redColor = 0xFFFF0000.toInt()
        val tileFile = createRawTileFile("tile_0000.bin", width, heightPerTile, redColor)
        val tiles = listOf(TileMetadata(0, tileFile, width, heightPerTile, 0))

        val outputFile = File(tempFolder.root, "output_red.png")
        FileOutputStream(outputFile).use { out ->
            assembler.assemble(tiles, out)
        }

        assertTrue("Output PNG must exist", outputFile.exists())
        assertTrue("Output PNG length must be positive", outputFile.length() > 0)

        // Verify with pure decoder
        val image = decodePng(outputFile)
        assertNotNull("Must successfully decode exported PNG", image)
        assertEquals("Width must match", width, image.width)
        assertEquals("Height must match", heightPerTile, image.height)

        // Verify Red channel (check center pixel): ARGB
        val pixel = image.getPixel(width / 2, heightPerTile / 2)
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        assertEquals("Red channel must be 255 (no channel swap)", 255, r)
        assertEquals("Green channel must be 0", 0, g)
        assertEquals("Blue channel must be 0 (not swapped with Red)", 0, b)
    }

    @Test
    fun `assemble multiple tiles should seamlessly stitch vertically`() {
        val blueColor = 0xFF0000FF.toInt()
        val greenColor = 0xFF00FF00.toInt()
        val tile1 = TileMetadata(0, createRawTileFile("tile_0.bin", width, 50, blueColor), width, 50, 0)
        val tile2 = TileMetadata(1, createRawTileFile("tile_1.bin", width, 70, greenColor), width, 70, 50)

        val outputFile = File(tempFolder.root, "output_multi.png")
        FileOutputStream(outputFile).use { out ->
            assembler.assemble(listOf(tile1, tile2), out)
        }

        val image = decodePng(outputFile)
        assertNotNull("Must decode multi-tile PNG", image)
        assertEquals("Total width", width, image.width)
        assertEquals("Total height must be sum of tile heights", 120, image.height)

        // Sample top tile (Blue)
        val topPixel = image.getPixel(width / 2, 25)
        assertEquals("Top blue channel", 255, topPixel and 0xFF)
        assertEquals("Top red channel", 0, (topPixel ushr 16) and 0xFF)

        // Sample bottom tile (Green)
        val bottomPixel = image.getPixel(width / 2, 85)
        assertEquals("Bottom green channel", 255, (bottomPixel ushr 8) and 0xFF)
        assertEquals("Bottom blue channel", 0, bottomPixel and 0xFF)
    }

    @Test
    fun `chunked IDAT binary structure should contain valid PNG magic and IDAT chunks`() {
        val tileFile = createRawTileFile("tile_large.bin", 200, 300, 0xFF778899.toInt())
        val tiles = listOf(TileMetadata(0, tileFile, 200, 300, 0))
        val outputFile = File(tempFolder.root, "output_chunked.png")

        FileOutputStream(outputFile).use { out ->
            assembler.assemble(tiles, out)
        }

        val bytes = outputFile.readBytes()
        // 1. Check 8-byte PNG signature: 89 50 4E 47 0D 0A 1A 0A
        val expectedMagic = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        for (i in expectedMagic.indices) {
            assertEquals("Magic byte $i", expectedMagic[i], bytes[i])
        }

        // 2. Scan chunks
        var offset = 8
        var idatCount = 0
        var hasIhdr = false
        var hasIend = false

        while (offset + 8 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val typeStr = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            when (typeStr) {
                "IHDR" -> hasIhdr = true
                "IDAT" -> idatCount++
                "IEND" -> hasIend = true
            }
            offset += 4 + 4 + length + 4 // length + type + data + crc
        }

        assertTrue("Must contain IHDR", hasIhdr)
        assertTrue("Must contain at least 1 IDAT", idatCount >= 1)
        assertTrue("Must contain IEND", hasIend)
    }

    @Test
    fun `assembleWithPagination should split output into independent valid PNGs`() {
        val tile1 = TileMetadata(0, createRawTileFile("p_tile_1.bin", width, 100, 0xFF111111.toInt()), width, 100, 0)
        val tile2 = TileMetadata(1, createRawTileFile("p_tile_2.bin", width, 100, 0xFF222222.toInt()), width, 100, 100)
        val tile3 = TileMetadata(2, createRawTileFile("p_tile_3.bin", width, 100, 0xFF333333.toInt()), width, 100, 200)

        val outputFiles = mutableListOf<File>()
        assembler.assembleWithPagination(
            tiles = listOf(tile1, tile2, tile3),
            maxSegmentHeight = 150
        ) { segmentIndex ->
            val segFile = File(tempFolder.root, "part_$segmentIndex.png")
            outputFiles.add(segFile)
            FileOutputStream(segFile)
        }

        assertTrue("Should have multiple parts", outputFiles.size >= 2)
        for (file in outputFiles) {
            val img = decodePng(file)
            assertNotNull("Each part must be a valid readable PNG", img)
            assertEquals("Width must match", width, img.width)
            assertTrue("Height must be positive", img.height > 0)
        }
    }

    private fun createRawTileFile(name: String, w: Int, h: Int, color: Int): File {
        val file = File(tempFolder.root, name)
        FileOutputStream(file).use { out ->
            val row = ByteBuffer.allocate(w * 4)
            for (x in 0 until w) row.putInt(color)
            val rowBytes = row.array()
            for (y in 0 until h) {
                out.write(rowBytes)
            }
        }
        return file
    }

    private data class DecodedPng(
        val width: Int,
        val height: Int,
        val pixelsRgba: ByteArray
    ) {
        fun getPixel(x: Int, y: Int): Int {
            val offset = (y * width + x) * 4
            val r = pixelsRgba[offset].toInt() and 0xFF
            val g = pixelsRgba[offset + 1].toInt() and 0xFF
            val b = pixelsRgba[offset + 2].toInt() and 0xFF
            val a = pixelsRgba[offset + 3].toInt() and 0xFF
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun decodePng(file: File): DecodedPng {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buf.position(8) // Skip PNG magic

        // Read IHDR
        val ihdrLen = buf.int
        val ihdrType = ByteArray(4)
        buf.get(ihdrType)
        val width = buf.int
        val height = buf.int
        buf.position(buf.position() + 5 + 4) // bitDepth, colorType, comp, filter, interlace + CRC

        // Collect all IDAT chunk payloads
        val idatBytes = ByteArrayOutputStream()
        while (buf.position() + 8 <= bytes.size) {
            val len = buf.int
            val type = ByteArray(4)
            buf.get(type)
            val typeStr = String(type, Charsets.US_ASCII)
            if (typeStr == "IDAT") {
                val chunkData = ByteArray(len)
                buf.get(chunkData)
                idatBytes.write(chunkData)
                buf.int // CRC
            } else if (typeStr == "IEND") {
                break
            } else {
                buf.position(buf.position() + len + 4)
            }
        }

        // Decompress IDAT stream with InflaterInputStream
        val decompressed = InflaterInputStream(ByteArrayInputStream(idatBytes.toByteArray()))
        val rawPixels = ByteArray(width * height * 4)
        val rowBuffer = ByteArray(1 + width * 4)
        var pixelOffset = 0

        for (y in 0 until height) {
            var read = 0
            while (read < rowBuffer.size) {
                val count = decompressed.read(rowBuffer, read, rowBuffer.size - read)
                if (count < 0) break
                read += count
            }
            assertEquals("Scanline filter type must be 0 (None)", 0.toByte(), rowBuffer[0])
            System.arraycopy(rowBuffer, 1, rawPixels, pixelOffset, width * 4)
            pixelOffset += width * 4
        }

        return DecodedPng(width, height, rawPixels)
    }
}
