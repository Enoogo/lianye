package org.lianye.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lianye.engine.model.TileMetadata
import java.io.File

class WeavingStateTest {

    @Test
    fun `test state hierarchy and properties`() {
        val idle = WeavingState.Idle(isServiceConnected = true)
        assertTrue(idle.isServiceConnected)

        val weaving = WeavingState.Weaving(frameCount = 5, currentHeightPx = 2500)
        assertEquals(5, weaving.frameCount)
        assertEquals(2500, weaving.currentHeightPx)

        val tile = TileMetadata(0, File("t0"), 100, 1000, 0)
        val preview = WeavingState.Preview(tiles = listOf(tile), totalHeightPx = 1000)
        assertEquals(1, preview.tiles.size)
        assertEquals(1000, preview.totalHeightPx)

        val error = WeavingState.Error("Security window blocked", listOf(tile))
        assertEquals("Security window blocked", error.message)
        assertEquals(1, error.partialTiles.size)
    }
}
