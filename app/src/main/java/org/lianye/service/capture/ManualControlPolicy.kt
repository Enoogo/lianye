package org.lianye.service.capture

import org.lianye.domain.model.ManualControl

internal object ManualControlPolicy {
    fun resolve(overlayAvailable: Boolean): ManualControl =
        if (overlayAvailable) ManualControl.OVERLAY else ManualControl.UNAVAILABLE
}
