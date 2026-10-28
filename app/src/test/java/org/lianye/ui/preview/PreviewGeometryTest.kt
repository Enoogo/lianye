package org.lianye.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.lianye.domain.model.ImageRect

class PreviewGeometryTest {
    @Test
    fun `reverse drag covers touched pixels and respects crop boundaries`() {
        assertEquals(
            ImageRect(10, 21, 35, 50),
            PreviewGeometry.selection(34.2f, 56f, 3f, 21.9f, ImageRect(10, 20, 40, 50))
        )
        assertNull(PreviewGeometry.selection(0f, 0f, 4f, 4f, ImageRect(10, 20, 40, 50)))
    }

    @Test
    fun `viewport stays within crop for both short and very long captures`() {
        val crop = ImageRect(10, 200, 1010, 50_200)
        assertEquals(200f, PreviewGeometry.clampOffset(-5f, crop, 1000f, 1f), 0f)
        assertEquals(49_200f, PreviewGeometry.clampOffset(50_500f, crop, 1000f, 1f), 0f)
        assertEquals(200f, PreviewGeometry.maxOffset(ImageRect(0, 200, 1000, 500), 1000f, 1f), 0f)
    }
}
