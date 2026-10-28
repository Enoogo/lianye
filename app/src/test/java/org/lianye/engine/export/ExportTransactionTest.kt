package org.lianye.engine.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class ExportTransactionTest {
    @Test
    fun `null stream rolls back every created part and publishes none`() {
        val deleted = mutableListOf<Int>()
        val published = mutableListOf<Int>()
        try {
            ExportTransaction.write(
                create = { it }, open = { if (it == 1) null else ByteArrayOutputStream() },
                writeSegments = { factory -> factory(0).use { it.write(1) }; factory(1).use { it.write(2) } },
                publish = { published.add(it); true }, rollback = { deleted.add(it) }
            )
            throw AssertionError("Expected missing stream to fail")
        } catch (_: IOException) {
            assertEquals(listOf(0, 1), deleted)
            assertTrue(published.isEmpty())
        }
    }

    @Test
    fun `publication failure rolls back all parts including already published ones`() {
        val deleted = mutableListOf<Int>()
        try {
            ExportTransaction.write(
                create = { it }, open = { ByteArrayOutputStream() },
                writeSegments = { factory -> repeat(3) { index -> factory(index).use { it.write(index) } } },
                publish = { it != 1 }, rollback = { deleted.add(it) }
            )
            throw AssertionError("Expected publication failure")
        } catch (_: IOException) {
            assertEquals(listOf(0, 1, 2), deleted)
        }
    }

    @Test
    fun `successful transaction returns every part in order`() {
        val published = mutableListOf<Int>()
        val result = ExportTransaction.write(
            create = { it }, open = { ByteArrayOutputStream() },
            writeSegments = { factory -> repeat(3) { index -> factory(index).close() } },
            publish = { published.add(it); true }, rollback = { throw AssertionError("Unexpected rollback") }
        )
        assertEquals(listOf(0, 1, 2), result)
        assertEquals(result, published)
    }
}
