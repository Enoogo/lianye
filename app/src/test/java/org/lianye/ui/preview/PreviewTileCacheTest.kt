package org.lianye.ui.preview

import org.junit.Assert.assertThrows
import org.junit.Test
import org.lianye.domain.model.Draft
import org.lianye.engine.model.TileMetadata
import java.io.File

class PreviewTileCacheTest {
    @Test fun invalidLayoutScaleIsRejectedBeforeSamplingOrReadingFiles() {
        val draft = Draft("invalid-layout", listOf(TileMetadata(0, File("not-read.raw"), 1, 1, 0)))
        val cache = PreviewTileCache()
        for (scale in listOf(Float.NaN, 0f, -1f, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { cache.visible(draft, 0f, 0f, scale) }
        }
    }
}
