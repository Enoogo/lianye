package org.lianye.platform

/** A settings switch and a running service are separate facts. Unknown restrictions stay unknown. */
enum class AccessibilityHelpStatus { OFF, CONNECTED, NOT_CONNECTED, RESTRICTED }

data class AccessibilityHelpState(
    val status: AccessibilityHelpStatus = AccessibilityHelpStatus.OFF,
    val sdkInt: Int = 29,
    val restriction: RestrictionStatus = RestrictionStatus.NOT_APPLICABLE
) {
    val showRestrictedHelp: Boolean
        get() = sdkInt >= 33 && status in listOf(AccessibilityHelpStatus.OFF, AccessibilityHelpStatus.RESTRICTED)

    companion object {
        fun resolve(connected: Boolean, enabled: Boolean, restriction: RestrictionStatus, sdkInt: Int): AccessibilityHelpState {
            val applicable = if (sdkInt < 33) RestrictionStatus.NOT_APPLICABLE else restriction
            val status = when {
                connected -> AccessibilityHelpStatus.CONNECTED
                enabled -> AccessibilityHelpStatus.NOT_CONNECTED
                applicable == RestrictionStatus.BLOCKED -> AccessibilityHelpStatus.RESTRICTED
                else -> AccessibilityHelpStatus.OFF
            }
            return AccessibilityHelpState(status, sdkInt, applicable)
        }
    }
}
