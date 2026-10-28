package org.lianye.data.draft

import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.Draft
import org.lianye.domain.model.EditSnapshot
import org.lianye.domain.model.ImageRect
import org.lianye.engine.model.TileMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

/** A single durable draft. Tile data must be committed before publishing its manifest. */
class DraftStore(private val root: File) {
    val tilesDirectory = File(root, "tiles")
    private val manifest = File(root, "draft.bin")
    private val backup = File(root, "draft.backup")
    private val pending = File(root, "draft.pending")

    init {
        check(tilesDirectory.isDirectory || tilesDirectory.mkdirs()) { "Cannot create draft directory" }
    }

    /** An interrupted manifest replacement can fall back to its last committed version. */
    @Synchronized
    fun load(): Draft? = sequenceOf(manifest, backup).mapNotNull { file ->
        runCatching { read(file) }.getOrNull()
    }.firstOrNull()

    @Synchronized
    fun save(draft: Draft) {
        validate(draft)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeUTF(draft.id)
            out.writeInt(draft.tiles.size)
            draft.tiles.forEach { tile ->
                out.writeInt(tile.index)
                out.writeUTF(relativeTilePath(tile.file))
                out.writeInt(tile.width)
                out.writeInt(tile.height)
                out.writeInt(tile.startY)
            }
            out.writeInt(draft.history.size)
            draft.history.forEach { out.writeSnapshot(it) }
            out.writeInt(draft.editIndex)
            out.writeInt(draft.revision)
            out.writeInt(draft.savedRevision)
            out.writeFloat(draft.viewOffsetY)
            out.writeFloat(draft.viewScale)
            out.writeUTF(draft.completion.name)
            out.writeBoolean(draft.savedSnapshot != null)
            draft.savedSnapshot?.let { out.writeSnapshot(it) }
        }
        val payload = buffer.toByteArray()
        require(payload.size <= MAX_MANIFEST_BYTES) { "Draft metadata is too large" }
        FileOutputStream(pending).use { stream ->
            val out = DataOutputStream(stream)
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value)
            out.write(payload)
            out.flush()
            stream.fd.sync()
        }
        // Preserve only a verified manifest, so a damaged primary cannot replace a valid backup.
        if (manifest.isFile && runCatching { read(manifest) }.getOrNull() != null) {
            replace(manifest, backup)
        }
        replace(pending, manifest)
    }

    /** Explicit discard only. Construction and recovery never remove image data. */
    @Synchronized
    fun clear() {
        listOf(manifest, backup, pending).forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("Cannot remove draft metadata")
        }
        val directoryPath = tilesDirectory.canonicalFile.toPath()
        tilesDirectory.listFiles()?.forEach { file ->
            require(file.canonicalFile.toPath().startsWith(directoryPath)) { "Unsafe draft tile path" }
            if (file.isFile && !file.delete()) throw IOException("Cannot remove draft tile")
        }
    }

    private fun read(file: File): Draft? {
        if (!file.isFile || file.length() > MAX_MANIFEST_BYTES + 20L) return null
        val (version, payload) = DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == MAGIC) { "Invalid draft magic" }
            val version = input.readInt()
            require(version in 1..VERSION) { "Invalid draft version" }
            val size = input.readInt()
            require(size in 1..MAX_MANIFEST_BYTES) { "Invalid manifest length" }
            val checksum = input.readLong()
            val bytes = ByteArray(size)
            input.readFully(bytes)
            require(input.read() == -1 && CRC32().apply { update(bytes) }.value == checksum) { "Invalid manifest checksum" }
            version to bytes
        }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val id = input.readUTF()
            val tiles = List(input.readCount(MAX_TILES)) {
                val index = input.readInt()
                val filePath = input.readUTF()
                TileMetadata(index, resolveTilePath(filePath), input.readInt(), input.readInt(), input.readInt())
            }
            val history = List(input.readCount(MAX_HISTORY)) { input.readSnapshot() }
            var draft = Draft(id, tiles, history, input.readInt(), input.readInt(), input.readInt(),
                input.readFloat(), input.readFloat(), CaptureCompletion.valueOf(input.readUTF()))
            if (version >= 2) {
                draft = draft.copy(savedSnapshot = if (input.readBoolean()) input.readSnapshot() else null)
            } else if (draft.savedRevision == draft.revision) {
                // V1 recorded only a revision. Its current saved content is knowable;
                // an older saved edit cannot be reconstructed after undo/redo/branching.
                draft = draft.copy(savedSnapshot = draft.canonicalSnapshot())
            }
            require(input.read() == -1) { "Unexpected manifest data" }
            validate(draft)
            draft
        }
    }

    private fun validate(draft: Draft) {
        require(draft.id.isNotBlank() && draft.id.length <= 256) { "Invalid draft ID" }
        require(draft.tiles.size in 1..MAX_TILES) { "Draft must contain committed tiles" }
        val width = draft.tiles.first().width
        require(width in 1..MAX_DIMENSION) { "Invalid tile width" }
        var nextY = 0L
        draft.tiles.forEachIndexed { index, tile ->
            require(tile.index == index && tile.width == width && tile.height in 1..MAX_DIMENSION && tile.startY.toLong() == nextY) { "Invalid tile sequence" }
            val checkedFile = resolveTilePath(relativeTilePath(tile.file))
            require(checkedFile.isFile && checkedFile.length() == tile.width.toLong() * tile.height * 4L) { "Incomplete draft tile" }
            nextY += tile.height
            require(nextY <= Int.MAX_VALUE) { "Draft is too tall" }
        }
        require(draft.history.size in 1..MAX_HISTORY && draft.editIndex in draft.history.indices) { "Invalid edit history" }
        require(draft.revision >= 0 && draft.savedRevision in -1..draft.revision) { "Invalid draft revision" }
        require(draft.viewOffsetY.isFinite() && draft.viewOffsetY >= 0f && draft.viewScale.isFinite() && draft.viewScale > 0f) { "Invalid viewport" }
        require(draft.savedSnapshot == null || draft.savedRevision >= 0) { "Saved content needs an exported revision" }
        (draft.history + listOfNotNull(draft.savedSnapshot)).forEach { edit ->
            require(edit.masks.size <= MAX_MASKS) { "Too many masks" }
            (listOfNotNull(edit.crop) + edit.masks).forEach { rect ->
                require(rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= nextY && rect.width > 0 && rect.height > 0) { "Invalid edit rectangle" }
            }
        }
    }

    private fun relativeTilePath(file: File): String {
        val directory = tilesDirectory.canonicalFile.toPath()
        val path = file.canonicalFile.toPath()
        require(path != directory && path.startsWith(directory)) { "Tile must be inside draft storage" }
        return directory.relativize(path).toString().replace(File.separatorChar, '/')
    }

    private fun resolveTilePath(relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute && !relative.contains('\\') && !relative.contains(':')) { "Invalid tile path" }
        require(relative.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "Unsafe tile path" }
        val directory = tilesDirectory.canonicalFile
        val file = File(directory, relative).canonicalFile
        require(file.toPath().startsWith(directory.toPath()) && file != directory) { "Unsafe tile path" }
        return file
    }

    private fun replace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun DataOutputStream.writeRect(rect: ImageRect) {
        writeInt(rect.left); writeInt(rect.top); writeInt(rect.right); writeInt(rect.bottom)
    }
    private fun DataOutputStream.writeSnapshot(snapshot: EditSnapshot) {
        writeBoolean(snapshot.crop != null)
        snapshot.crop?.let { writeRect(it) }
        writeInt(snapshot.masks.size)
        snapshot.masks.forEach { writeRect(it) }
    }
    private fun DataInputStream.readSnapshot(): EditSnapshot {
        val crop = if (readBoolean()) readRect() else null
        val masks = List(readCount(MAX_MASKS)) { readRect() }
        return EditSnapshot(crop, masks)
    }
    private fun DataInputStream.readRect() = ImageRect(readInt(), readInt(), readInt(), readInt())
    private fun DataInputStream.readCount(maximum: Int) = readInt().also { require(it in 0..maximum) { "Invalid metadata count" } }

    companion object {
        private const val MAGIC = 0x4C4F4F4D
        private const val VERSION = 2
        private const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
        private const val MAX_TILES = 100_000
        private const val MAX_HISTORY = 10_000
        private const val MAX_MASKS = 10_000
        private const val MAX_DIMENSION = Int.MAX_VALUE
    }
}
