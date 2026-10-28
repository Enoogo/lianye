package org.lianye.ui.floating

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class FloatingLifecycleBridgeTest {

    private lateinit var bridge: FloatingLifecycleBridge

    @Before
    fun setUp() {
        bridge = FloatingLifecycleBridge()
    }

    @Test
    fun `initial lifecycle state should be INITIALIZED`() {
        assertEquals(Lifecycle.State.INITIALIZED, bridge.lifecycle.currentState)
        assertNotNull(bridge.viewModelStore)
        assertNotNull(bridge.savedStateRegistry)
    }
}
