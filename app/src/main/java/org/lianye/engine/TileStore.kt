package org.lianye.engine

import org.lianye.engine.model.PixelSlice
import org.lianye.engine.model.TileMetadata
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.io.RandomAccessFile
import java.util.UUID

class TileStore(
    private val storageDir: File,
    val chunkTargetHeight: Int = 1024,
    private val onCheckpoint: (List<TileMetadata>) -> Unit = {}
) {
    private val tiles = mutableListOf<TileMetadata>()
    private val ownedFiles = mutableSetOf<File>()
    private var sessionId = UUID.randomUUID().toString()
    private var currentTileIndex = 0
    private var currentTileHeight = 0
    private var currentTileStartY = 0
    private var currentFile: File? = null
    private var currentStream: BufferedOutputStream? = null
    private var currentFileStream: FileOutputStream? = null
    private var initialWidth = -1

    private var rowBuffer = ByteArray(0)

    init {
        require(chunkTargetHeight > 0) { "chunkTargetHeight must be positive" }
        check(storageDir.isDirectory || storageDir.mkdirs()) { "Cannot create tile storage" }
    }

    val currentHeightPx: Int get() = tiles.sumOf { it.height } + currentTileHeight
    val availableStorageBytes: Long get() = storageDir.usableSpace
    fun committedTiles(): List<TileMetadata> = tiles.toList()

    fun appendStrip(strip: PixelSlice) {
        if (strip.height <= 0) return
        validateWidth(strip.width)
        ensureRowBufferCapacity(strip.width * 4)

        for (y in 0 until strip.height) {
            if (shouldRotateTile()) {
                rotateTile()
            }
            writeRow(strip, y)
            if (currentTileHeight >= chunkTargetHeight) closeCurrentTile()
        }
    }

    fun finalizeTiles(): List<TileMetadata> {
        closeCurrentTile()
        return tiles.toList()
    }

    /** Retains only complete rows when a write or flush was interrupted. */
    fun salvageTiles(): List<TileMetadata> {
        if (runCatching { finalizeTiles() }.isSuccess && currentFile == null) return tiles.toList()
        runCatching { currentStream?.close() }
        currentStream = null
        currentFileStream = null
        val file = currentFile
        if (file != null && file.isFile && initialWidth > 0) {
            val rowBytes = initialWidth.toLong() * 4L
            val completeRows = minOf(currentTileHeight.toLong(), file.length() / rowBytes).toInt()
            if (completeRows > 0) runCatching {
                RandomAccessFile(file, "rw").use { raw ->
                    raw.setLength(completeRows * rowBytes)
                    raw.fd.sync()
                }
                val committed = File(storageDir, file.name.removeSuffix(".part") + ".bin")
                commitFile(file, committed)
                ownedFiles.remove(file)
                ownedFiles.add(committed)
                tiles.add(TileMetadata(currentTileIndex++, committed, initialWidth, completeRows, currentTileStartY))
                currentFile = null
                currentTileHeight = 0
                onCheckpoint(tiles.toList())
            }
        }
        return tiles.toList()
    }

    fun clear() {
        // Discard only this instance's capture. Other sessions may be referenced by a draft.
        currentStream?.close()
        currentStream = null
        currentFileStream = null
        ownedFiles.forEach { file -> check(!file.exists() || file.delete()) { "Cannot discard capture tile" } }
        ownedFiles.clear()
        tiles.clear()
        resetState()
        sessionId = UUID.randomUUID().toString()
    }

    private fun validateWidth(w: Int) {
        require(w > 0 && w <= Int.MAX_VALUE / 4) { "Invalid strip width" }
        if (initialWidth == -1) {
            initialWidth = w
        } else {
            require(w == initialWidth) {
                "Strip width mismatch: expected $initialWidth but was $w"
            }
        }
    }

    private fun shouldRotateTile(): Boolean {
        return currentStream == null || currentTileHeight >= chunkTargetHeight
    }

    private fun rotateTile() {
        closeCurrentTile()
        val file = File(storageDir, "${sessionId}_tile_${currentTileIndex}.part")
        currentFile = file
        ownedFiles.add(file)
        currentFileStream = FileOutputStream(file)
        currentStream = BufferedOutputStream(currentFileStream!!, 64 * 1024)
        currentTileStartY = if (tiles.isEmpty()) 0 else tiles.last().startY + tiles.last().height
        currentTileHeight = 0
    }

    private fun closeCurrentTile() {
        currentStream?.let { stream ->
            stream.flush()
            currentFileStream?.fd?.sync()
            stream.close()
            currentStream = null
            currentFileStream = null
            val file = currentFile ?: return
            if (currentTileHeight > 0) {
                val committed = File(storageDir, file.name.removeSuffix(".part") + ".bin")
                commitFile(file, committed)
                ownedFiles.remove(file)
                ownedFiles.add(committed)
                tiles.add(
                    TileMetadata(
                        index = currentTileIndex++,
                        file = committed,
                        width = initialWidth,
                        height = currentTileHeight,
                        startY = currentTileStartY
                    )
                )
            } else {
                file.delete()
                ownedFiles.remove(file)
            }
            currentFile = null
            currentTileHeight = 0
            onCheckpoint(tiles.toList())
        }
    }

    private fun writeRow(strip: PixelSlice, y: Int) {
        var offset = 0
        for (x in 0 until strip.width) {
            val pixel = strip.getPixel(x, y)
            rowBuffer[offset++] = ((pixel ushr 24) and 0xFF).toByte()
            rowBuffer[offset++] = ((pixel ushr 16) and 0xFF).toByte()
            rowBuffer[offset++] = ((pixel ushr 8) and 0xFF).toByte()
            rowBuffer[offset++] = (pixel and 0xFF).toByte()
        }
        currentStream?.write(rowBuffer, 0, offset)
        currentTileHeight++
    }

    private fun ensureRowBufferCapacity(neededBytes: Int) {
        if (rowBuffer.size < neededBytes) {
            rowBuffer = ByteArray(neededBytes)
        }
    }

    private fun commitFile(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun resetState() {
        currentTileIndex = 0
        currentTileHeight = 0
        currentTileStartY = 0
        currentFile = null
        currentStream = null
        currentFileStream = null
        initialWidth = -1
    }
}
