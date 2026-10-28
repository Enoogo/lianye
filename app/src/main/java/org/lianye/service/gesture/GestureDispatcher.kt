package org.lianye.service.gesture

import org.lianye.service.gesture.model.ControlledScrollCommand

interface GestureDispatcher {
    fun sendGesture(command: ControlledScrollCommand, callback: (Boolean) -> Unit): Boolean
    fun sendCancelGesture(): Boolean
}
