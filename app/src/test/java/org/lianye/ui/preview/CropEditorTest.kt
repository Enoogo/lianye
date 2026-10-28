package org.lianye.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lianye.domain.model.ImageRect

class CropEditorTest {
    private val bounds = ImageRect(0, 0, 1080, 8400)
    private val crop = ImageRect(100, 200, 900, 7000)

    @Test fun eachCornerKeepsTheOppositeCornerFixed() {
        assertEquals(ImageRect(150, 300, 900, 7000), CropGeometry.moveCorner(crop, CropCorner.TOP_LEFT, 150f, 300f, bounds))
        assertEquals(ImageRect(100, 300, 850, 7000), CropGeometry.moveCorner(crop, CropCorner.TOP_RIGHT, 850f, 300f, bounds))
        assertEquals(ImageRect(150, 200, 900, 6900), CropGeometry.moveCorner(crop, CropCorner.BOTTOM_LEFT, 150f, 6900f, bounds))
        assertEquals(ImageRect(100, 200, 850, 6900), CropGeometry.moveCorner(crop, CropCorner.BOTTOM_RIGHT, 850f, 6900f, bounds))
    }

    @Test fun cornerCannotCrossItsOppositeCornerOrImageBoundary() {
        assertEquals(ImageRect(899, 6999, 900, 7000), CropGeometry.moveCorner(crop, CropCorner.TOP_LEFT, 5000f, 9000f, bounds))
        assertEquals(ImageRect(100, 200, 101, 201), CropGeometry.moveCorner(crop, CropCorner.BOTTOM_RIGHT, -500f, -500f, bounds))
        assertEquals(ImageRect(0, 0, 900, 7000), CropGeometry.moveCorner(crop, CropCorner.TOP_LEFT, -500f, -500f, bounds))
        assertEquals(ImageRect(100, 200, 1080, 8400), CropGeometry.moveCorner(crop, CropCorner.BOTTOM_RIGHT, 5000f, 9000f, bounds))
    }

    @Test fun localUndoRedoAndResetDoNotChangeCommittedRect() {
        val original = CropHistory(listOf(crop))
        val changed = original.change(ImageRect(150, 300, 850, 6900))
        assertEquals(crop, original.current)
        assertTrue(changed.canUndo)
        assertEquals(crop, changed.undo().current)
        assertEquals(changed.current, changed.undo().redo().current)
        assertEquals(bounds, changed.change(bounds).current)
    }

    @Test fun aNewChangeAfterUndoReplacesRedoBranch() {
        val first = ImageRect(150, 300, 850, 6900)
        val second = ImageRect(175, 350, 800, 6800)
        val third = ImageRect(125, 250, 875, 6950)
        val state = CropHistory(listOf(crop)).change(first).change(second).undo().change(third)
        assertEquals(listOf(crop, first, third), state.entries)
        assertFalse(state.canRedo)
        assertEquals(state, state.change(third))
    }
}
