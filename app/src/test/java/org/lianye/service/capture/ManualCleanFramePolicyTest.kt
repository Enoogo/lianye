package org.lianye.service.capture

import org.junit.Assert.*
import org.junit.Test
import org.lianye.domain.model.ManualControl

class ManualCleanFramePolicyTest {
    @Test fun `quiet notification capture can reuse a clean sample but overlay switching requires recapture`() {
        assertTrue(ManualCleanFramePolicy.canReuseCandidate(ManualControl.NOTIFICATION, false, true, false))
        assertFalse(ManualCleanFramePolicy.canReuseCandidate(ManualControl.OVERLAY, false, true, false))
        // Removing an overlay does not make a previously cached raw frame safe to persist.
        assertFalse(ManualCleanFramePolicy.canReuseCandidate(ManualControl.NOTIFICATION, true, true, false))
    }

    @Test fun `clean sample optimization cannot bypass session validity or product visibility`() {
        assertFalse(ManualCleanFramePolicy.canReuseCandidate(ManualControl.NOTIFICATION, false, false, false))
        assertFalse(ManualCleanFramePolicy.canReuseCandidate(ManualControl.NOTIFICATION, false, true, true))
        assertFalse(ManualCleanFramePolicy.canReuseCandidate(ManualControl.UNAVAILABLE, false, true, false))
    }
}
