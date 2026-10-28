package org.lianye.service.gesture

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScrollCommandFactoryTest {

    private val region = Rect(0, 300, 1080, 2100)

    @Test
    fun `stroke starts inside the band`() {
        val command = AutoScrollCommandFactory.build(region, null, 2.5f)
        assertEquals(540f, command.startX, 0.5f)
        assertTrue(command.startY > region.top)
        assertTrue(command.endY > region.top)
        assertTrue(command.startY < region.bottom)
    }

    @Test
    fun `stroke avoids a bubble sitting at the centre`() {
        val bubble = Rect(514, 1174, 566, 1226)
        val command = AutoScrollCommandFactory.build(region, bubble, 2.5f)
        assertNotEquals("must not start on the control", 540f, command.startX, 1f)
        assertTrue(command.startX >= region.left)
        assertTrue(command.startX <= region.right)
    }

    @Test
    fun `stroke avoids a bubble near the left edge`() {
        val bubble = Rect(20, 1100, 72, 1152)
        val command = AutoScrollCommandFactory.build(region, bubble, 2.5f)
        assertTrue("x=${command.startX}", command.startX > 132f)
    }
}
