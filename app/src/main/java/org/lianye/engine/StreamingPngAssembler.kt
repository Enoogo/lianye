package org.lianye.engine

import org.lianye.engine.model.TileMetadata
import org.lianye.domain.model.Draft
import org.lianye.domain.model.ImageRect
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

class StreamingPngAssembler {

    companion object {
        private const val CHUNK_BUFFER_SIZE = 32 * 1024
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
    }

    fun assemble(tiles: List<TileMetadata>, outputStream: OutputStream) {
        assembleDraft(Draft("export", tiles), outputStream)
    }

    /** Streams edited source rows. Coordinates remain in the original image throughout. */
    fun assembleDraft(
        draft: Draft,
        outputStream: OutputStream,
        region: ImageRect = draft.crop,
        onProgress: (Float) -> Unit = {}
    ) {
        validate(draft)
        require(region.intersect(draft.crop) == region) { "Export region must be inside the crop" }
        writeRegion(draft, outputStream, region, onProgress)
    }

    private fun writeRegion(draft: Draft, outputStream: OutputStream, region: ImageRect, onProgress: (Float) -> Unit) {
        val width = region.width
        val totalHeight = region.height
        val crc = CRC32()
        val deflater = Deflater(1)

        try {
            outputStream.write(PNG_SIGNATURE)
            writeIhdr(outputStream, width, totalHeight, crc)
            streamIdatChunks(outputStream, draft, region, deflater, crc, onProgress)
            writeIend(outputStream, crc)
            outputStream.flush()
            onProgress(1f)
        } finally {
            deflater.end()
        }
    }

    fun assembleWithPagination(
        tiles: List<TileMetadata>,
        maxSegmentHeight: Int,
        outputFactory: (segmentIndex: Int) -> OutputStream
    ): List<Long> {
        return assembleDraftWithPagination(Draft("export", tiles), maxSegmentHeight, outputFactory)
    }

    /** Splits at exact row boundaries, including boundaries inside a stored tile. */
    fun assembleDraftWithPagination(
        draft: Draft,
        maxSegmentHeight: Int,
        outputFactory: (segmentIndex: Int) -> OutputStream,
        onProgress: (Float) -> Unit = {}
    ): List<Long> {
        validate(draft)
        require(maxSegmentHeight > 0) { "Segment height must be positive" }
        val segmentHeights = mutableListOf<Long>()
        val crop = draft.crop
        var top = crop.top
        var segmentIndex = 0
        onProgress(0f)
        while (top < crop.bottom) {
            val bottom = minOf(crop.bottom.toLong(), top.toLong() + maxSegmentHeight).toInt()
            val segment = ImageRect(crop.left, top, crop.right, bottom)
            outputFactory(segmentIndex++).use { output ->
                writeRegion(draft, output, segment) { progress ->
                    onProgress((((segment.top - crop.top) + progress * segment.height) / crop.height).coerceAtMost(0.999f))
                }
            }
            segmentHeights.add(segment.height.toLong())
            top = bottom
        }
        onProgress(1f)
        return segmentHeights
    }

    private fun validate(draft: Draft) {
        require(draft.tiles.isNotEmpty() && draft.width > 0 && draft.height > 0) { "Draft has no image" }
        require(draft.crop.width > 0 && draft.crop.height > 0) { "Crop must contain pixels" }
        var startY = 0L
        for (tile in draft.tiles) {
            require(tile.width == draft.width && tile.height > 0 && tile.startY.toLong() == startY) {
                "Tile dimensions or positions are inconsistent"
            }
            startY += tile.height
            require(startY <= Int.MAX_VALUE) { "Image is too tall" }
            if (tile.file.length() < tile.width.toLong() * tile.height * 4) {
                throw java.io.EOFException("Tile is incomplete: ${tile.index}")
            }
        }
    }

    private fun writeIhdr(out: OutputStream, w: Int, h: Int, crc: CRC32) {
        val data = ByteArray(13)
        writeInt32BE(data, 0, w)
        writeInt32BE(data, 4, h)
        data[8] = 8.toByte()  // Bit depth
        data[9] = 6.toByte()  // Color type: RGBA
        data[10] = 0.toByte() // Compression: Deflate
        data[11] = 0.toByte() // Filter: None
        data[12] = 0.toByte() // Interlace: None
        writeChunk(out, "IHDR", data, 0, data.size, crc)
    }

    private fun streamIdatChunks(
        out: OutputStream,
        draft: Draft,
        region: ImageRect,
        deflater: Deflater,
        crc: CRC32,
        onProgress: (Float) -> Unit
    ) {
        val rawRow = ByteArray(draft.width * 4)
        val scanline = ByteArray(1 + region.width * 4)
        scanline[0] = 0x00 // Filter None
        val chunkBuffer = ByteArray(CHUNK_BUFFER_SIZE)

        var written = 0
        onProgress(0f)
        for (tile in draft.tiles) {
            val firstY = maxOf(region.top, tile.startY)
            val lastY = minOf(region.bottom, tile.startY + tile.height)
            if (firstY >= lastY) continue
            FileInputStream(tile.file).use { fileInput ->
                fileInput.channel.position((firstY - tile.startY).toLong() * draft.width * 4)
                BufferedInputStream(fileInput, 64 * 1024).use { input ->
                    for (y in firstY until lastY) {
                        readFullRow(input, rawRow)
                        reorderArgbToRgba(rawRow, scanline, region.left, region.width)
                        for (mask in draft.edits.masks) {
                            if (y < mask.top || y >= mask.bottom) continue
                            val left = maxOf(region.left, mask.left)
                            val right = minOf(region.right, mask.right)
                            for (x in left until right) {
                                val offset = 1 + (x - region.left) * 4
                                scanline[offset] = 0
                                scanline[offset + 1] = 0
                                scanline[offset + 2] = 0
                                scanline[offset + 3] = 0xFF.toByte()
                            }
                        }
                        deflater.setInput(scanline, 0, scanline.size)
                        drainDeflater(out, deflater, chunkBuffer, crc)
                        written++
                        if (written % 128 == 0 || written == region.height) {
                            onProgress(written.toFloat() / region.height * 0.99f)
                        }
                    }
                }
            }
        }

        deflater.finish()
        drainDeflaterUntilFinished(out, deflater, chunkBuffer, crc)
    }

    private fun readFullRow(input: BufferedInputStream, rawRow: ByteArray) {
        var bytesRead = 0
        while (bytesRead < rawRow.size) {
            val count = input.read(rawRow, bytesRead, rawRow.size - bytesRead)
            if (count < 0) {
                throw java.io.EOFException("Unexpected end of tile file: read $bytesRead of ${rawRow.size} bytes")
            }
            bytesRead += count
        }
    }

    private fun reorderArgbToRgba(raw: ByteArray, scanline: ByteArray, left: Int, width: Int) {
        var src = left * 4
        var dst = 1
        for (x in 0 until width) {
            val a = raw[src++]
            val r = raw[src++]
            val g = raw[src++]
            val b = raw[src++]
            scanline[dst++] = r
            scanline[dst++] = g
            scanline[dst++] = b
            scanline[dst++] = a
        }
    }

    private fun drainDeflater(
        out: OutputStream,
        deflater: Deflater,
        buffer: ByteArray,
        crc: CRC32
    ) {
        while (!deflater.needsInput()) {
            val bytes = deflater.deflate(buffer, 0, buffer.size)
            if (bytes <= 0) break
            writeChunk(out, "IDAT", buffer, 0, bytes, crc)
        }
    }

    private fun drainDeflaterUntilFinished(
        out: OutputStream,
        deflater: Deflater,
        buffer: ByteArray,
        crc: CRC32
    ) {
        while (!deflater.finished()) {
            val bytes = deflater.deflate(buffer, 0, buffer.size)
            if (bytes <= 0) break
            writeChunk(out, "IDAT", buffer, 0, bytes, crc)
        }
    }

    private fun writeIend(out: OutputStream, crc: CRC32) {
        writeChunk(out, "IEND", ByteArray(0), 0, 0, crc)
    }

    private fun writeChunk(
        out: OutputStream,
        typeStr: String,
        data: ByteArray,
        offset: Int,
        len: Int,
        crc: CRC32
    ) {
        writeUInt32BE(out, len.toLong())
        val typeBytes = typeStr.toByteArray(Charsets.US_ASCII)
        crc.reset()
        crc.update(typeBytes)
        if (len > 0) {
            crc.update(data, offset, len)
        }
        out.write(typeBytes)
        if (len > 0) {
            out.write(data, offset, len)
        }
        writeUInt32BE(out, crc.value)
    }

    private fun writeUInt32BE(out: OutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun writeInt32BE(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }
}
