package org.lianye.service.gesture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.lianye.service.gesture.model.ControlledScrollCommand

@OptIn(ExperimentalCoroutinesApi::class)
class AntiFlingControllerTest {

    private lateinit var fakeDispatcher: FakeGestureDispatcher
    private lateinit var controller: AntiFlingController
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val sampleCommand = ControlledScrollCommand(
        startX = 500f, startY = 1600f,
        endX = 500f, endY = 800f,
        moveDurationMs = 600L,
        holdDurationMs = 150L
    )

    @Before
    fun setUp() {
        fakeDispatcher = FakeGestureDispatcher()
        controller = AntiFlingController(fakeDispatcher, timeoutMs = 1500L)
    }

    @Test
    fun `normal completion should return true and not send cancel`() = testScope.runTest {
        fakeDispatcher.autoComplete = true
        fakeDispatcher.completeSuccess = true

        val result = controller.executeControlledScroll(sampleCommand)

        assertTrue("Should return true on successful dispatch", result)
        assertEquals("Should dispatch gesture once", 1, fakeDispatcher.dispatchedCommands.size)
        assertEquals("Should not send cancel on success", 0, fakeDispatcher.cancelCount)
    }

    @Test
    fun `gesture failure callback should return false`() = testScope.runTest {
        fakeDispatcher.autoComplete = true
        fakeDispatcher.completeSuccess = false

        val result = controller.executeControlledScroll(sampleCommand)

        assertFalse("Should return false on failed gesture", result)
        assertEquals("Should not send cancel if callback completed with false", 0, fakeDispatcher.cancelCount)
    }

    @Test
    fun `watchdog timeout should send cancel gesture and return false`() = testScope.runTest {
        fakeDispatcher.autoComplete = false // Never calls callback

        var result: Boolean? = null
        val job = launch {
            result = controller.executeControlledScroll(sampleCommand)
        }

        advanceTimeBy(1600L)
        job.join()

        assertEquals(false, result)
        assertTrue("Watchdog timeout must send cancel gesture", fakeDispatcher.cancelCount >= 1)
    }

    @Test
    fun `parent job cancellation must trigger cancel gesture`() = testScope.runTest {
        fakeDispatcher.autoComplete = false

        val job = launch {
            controller.executeControlledScroll(sampleCommand)
        }

        advanceTimeBy(200L)
        job.cancelAndJoin()

        assertTrue("Parent job cancellation must trigger cancel gesture via invokeOnCancellation", fakeDispatcher.cancelCount >= 1)
    }
}

class FakeGestureDispatcher : GestureDispatcher {
    val dispatchedCommands = mutableListOf<ControlledScrollCommand>()
    var cancelCount = 0
    var autoComplete = true
    var completeSuccess = true
    var delayMs = 0L

    override fun sendGesture(command: ControlledScrollCommand, callback: (Boolean) -> Unit): Boolean {
        dispatchedCommands.add(command)
        if (autoComplete) {
            callback(completeSuccess)
        }
        return true
    }

    override fun sendCancelGesture(): Boolean {
        cancelCount++
        return true
    }
}
