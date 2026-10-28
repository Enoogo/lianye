package org.lianye.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import org.lianye.service.LianyeAccessibilityService

/** Optional service details may be protected by the ROM. Every route has an automatic fallback. */
object AccessibilitySettingsNavigator {
    enum class Destination { SERVICE, ACCESSIBILITY, SETTINGS, UNAVAILABLE }

    fun open(context: Context): Destination = launchCandidates(context) { context.startActivity(it) }

    internal fun launchCandidates(context: Context, launch: (Intent) -> Unit): Destination {
        val component = ComponentName(context, LianyeAccessibilityService::class.java)
        val candidates = listOf(
            Destination.SERVICE to Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.intent.extra.COMPONENT_NAME", component),
            Destination.ACCESSIBILITY to DeviceVendorDetector.createAccessibilityIntent(context),
            Destination.ACCESSIBILITY to Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Destination.SETTINGS to Intent(Settings.ACTION_SETTINGS)
        )
        for ((destination, intent) in candidates) {
            try {
                launch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return destination
            } catch (_: ActivityNotFoundException) {
                // Older and vendor settings apps need not implement every action.
            } catch (_: SecurityException) {
                // A system-only details action must never interrupt the public settings route.
            }
        }
        return Destination.UNAVAILABLE
    }

    fun isServiceEnabled(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return@runCatching false
        val ownService = ComponentName(context, LianyeAccessibilityService::class.java)
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            ComponentName.unflattenFromString(it.id) == ownService
        }
    }.getOrDefault(false)
}
