package org.lianye.service.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lianye.domain.model.ManualControl

class ManualControlPolicyTest {
    @Test fun missingOverlayRequiresPreparation() {
        assertEquals(ManualControl.UNAVAILABLE, ManualControlPolicy.resolve(false))
    }

    @Test fun availableOverlayKeepsVisibleGuidance() {
        assertEquals(ManualControl.OVERLAY, ManualControlPolicy.resolve(true))
    }
}
