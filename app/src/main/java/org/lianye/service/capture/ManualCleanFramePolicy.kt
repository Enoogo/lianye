package org.lianye.service.capture

import org.lianye.domain.model.ManualControl

/** Only notification sessions which never showed own surfaces have an already-clean candidate. */
internal object ManualCleanFramePolicy {
    fun canReuseCandidate(control: ManualControl, hasOwnWindowHistory: Boolean, projectionReady: Boolean, productVisible: Boolean): Boolean =
        control == ManualControl.NOTIFICATION && !hasOwnWindowHistory && projectionReady && !productVisible
}
