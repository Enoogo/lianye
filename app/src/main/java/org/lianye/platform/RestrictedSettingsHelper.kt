package org.lianye.platform

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 宿主系统受限制设置 (Restricted Settings) 安全探针。
 *
 * 核心安全规则：
 * 1. Android 10..12 无此机制，严格短路返回 false，杜绝底层抛出 IllegalArgumentException；
 * 2. Android 13+ (API 33) 探测 AppOp 状态；
 * 3. 未知结果与探测异常不等同于已允许，也不宣称明确被阻止。
 */
enum class RestrictionStatus { NOT_APPLICABLE, ALLOWED, BLOCKED, UNKNOWN }

object RestrictedSettingsHelper {

    private const val OPSTR_ACCESS_RESTRICTED_SETTINGS = "android:access_restricted_settings"

    fun isRestrictedBlocked(context: Context): Boolean = readStatus(context) == RestrictionStatus.BLOCKED

    fun readStatus(context: Context): RestrictionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return RestrictionStatus.NOT_APPLICABLE
        }
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return RestrictionStatus.UNKNOWN
            val mode = appOps.unsafeCheckOpNoThrow(
                OPSTR_ACCESS_RESTRICTED_SETTINGS,
                Process.myUid(),
                context.packageName
            )
            classify(Build.VERSION.SDK_INT, mode)
        } catch (_: Throwable) {
            RestrictionStatus.UNKNOWN
        }
    }

    internal fun classify(sdkInt: Int, mode: Int?): RestrictionStatus = when {
        sdkInt < 33 -> RestrictionStatus.NOT_APPLICABLE
        mode == AppOpsManager.MODE_ALLOWED -> RestrictionStatus.ALLOWED
        mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED -> RestrictionStatus.BLOCKED
        else -> RestrictionStatus.UNKNOWN
    }
}
