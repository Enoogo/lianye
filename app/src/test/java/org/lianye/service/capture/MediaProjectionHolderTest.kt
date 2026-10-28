package org.lianye.service.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaProjectionHolderTest {

    @Before
    fun setUp() {
        MediaProjectionHolder.clear()
    }

    @Test
    fun `holder should be empty initially`() {
        assertNull(MediaProjectionHolder.get())
        assertFalse(MediaProjectionHolder.isReady())
    }

    @Test
    fun `clear should reset holder state`() {
        MediaProjectionHolder.clear()
        assertNull(MediaProjectionHolder.get())
        assertFalse(MediaProjectionHolder.isReady())
    }
}
