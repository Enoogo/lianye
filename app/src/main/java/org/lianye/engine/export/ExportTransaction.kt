package org.lianye.engine.export

import java.io.IOException
import java.io.OutputStream

/** Keeps all gallery records recoverable until writing and publication both finish. */
internal object ExportTransaction {
    fun <T> write(
        create: (Int) -> T,
        open: (T) -> OutputStream?,
        writeSegments: ((Int) -> OutputStream) -> Unit,
        publish: (T) -> Boolean,
        rollback: (T) -> Unit
    ): List<T> {
        val created = mutableListOf<T>()
        try {
            writeSegments { index ->
                val record = create(index)
                created.add(record)
                open(record) ?: throw IOException("Could not open gallery image")
            }
            created.forEach { if (!publish(it)) throw IOException("Could not finish saving image") }
            return created.toList()
        } catch (error: Exception) {
            created.forEach { record ->
                try { rollback(record) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            }
            throw error
        }
    }
}
