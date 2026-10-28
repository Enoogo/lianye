package org.lianye.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.StringRes
import org.lianye.R
import org.lianye.service.LianyeAccessibilityService
import java.util.Locale

enum class DeviceVendor {
    XIAOMI,
    OPPO,
    ONEPLUS,
    REALME,
    VIVO,
    HUAWEI,
    HONOR,
    SAMSUNG,
    AOSP
}

/**
 * 宿主设备品牌静默感知引擎。
 * 零 UI 依赖，负责推导设备厂商分类，并映射行前微提示及生成焦点高亮 Intent。
 */
object DeviceVendorDetector {

    val currentVendor: DeviceVendor by lazy {
        resolveVendor(Build.MANUFACTURER, Build.BRAND)
    }

    fun resolveVendor(manufacturer: String?, brand: String?): DeviceVendor {
        val m = manufacturer?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val b = brand?.trim()?.lowercase(Locale.ROOT).orEmpty()
        fun matches(vararg names: String) = names.any { m.contains(it) || b.contains(it) }
        return when {
            matches("honor") -> DeviceVendor.HONOR
            matches("oneplus") -> DeviceVendor.ONEPLUS
            matches("realme") -> DeviceVendor.REALME
            matches("xiaomi", "redmi", "poco", "blackshark") -> DeviceVendor.XIAOMI
            matches("oppo") -> DeviceVendor.OPPO
            matches("vivo", "iqoo") -> DeviceVendor.VIVO
            matches("huawei") -> DeviceVendor.HUAWEI
            matches("samsung") -> DeviceVendor.SAMSUNG
            else -> DeviceVendor.AOSP
        }
    }

    @StringRes
    fun getPreFlightHintRes(vendor: DeviceVendor = currentVendor): Int {
        return when (vendor) {
            DeviceVendor.XIAOMI -> R.string.vendor_hint_xiaomi
            DeviceVendor.OPPO, DeviceVendor.ONEPLUS, DeviceVendor.REALME -> R.string.vendor_hint_oppo
            DeviceVendor.VIVO -> R.string.vendor_hint_vivo
            DeviceVendor.HUAWEI -> R.string.vendor_hint_huawei
            DeviceVendor.HONOR -> R.string.vendor_hint_honor
            DeviceVendor.SAMSUNG -> R.string.vendor_hint_samsung
            DeviceVendor.AOSP -> R.string.vendor_hint_aosp
        }
    }

    @StringRes
    fun getSettingsPathRes(vendor: DeviceVendor = currentVendor): Int = when (vendor) {
        DeviceVendor.XIAOMI -> R.string.vendor_path_xiaomi
        DeviceVendor.OPPO, DeviceVendor.ONEPLUS, DeviceVendor.REALME -> R.string.vendor_path_oppo
        DeviceVendor.VIVO -> R.string.vendor_path_vivo
        DeviceVendor.HUAWEI -> R.string.vendor_path_huawei
        DeviceVendor.HONOR -> R.string.vendor_path_honor
        DeviceVendor.SAMSUNG -> R.string.vendor_path_samsung
        DeviceVendor.AOSP -> R.string.vendor_path_aosp
    }

    @StringRes
    fun getNameRes(vendor: DeviceVendor = currentVendor): Int = when (vendor) {
        DeviceVendor.XIAOMI -> R.string.vendor_name_xiaomi
        DeviceVendor.OPPO -> R.string.vendor_name_oppo
        DeviceVendor.ONEPLUS -> R.string.vendor_name_oneplus
        DeviceVendor.REALME -> R.string.vendor_name_realme
        DeviceVendor.VIVO -> R.string.vendor_name_vivo
        DeviceVendor.HUAWEI -> R.string.vendor_name_huawei
        DeviceVendor.HONOR -> R.string.vendor_name_honor
        DeviceVendor.SAMSUNG -> R.string.vendor_name_samsung
        DeviceVendor.AOSP -> R.string.vendor_name_android
    }

    @StringRes
    fun getServiceListRes(vendor: DeviceVendor = currentVendor): Int = when (vendor) {
        DeviceVendor.XIAOMI -> R.string.accessibility_list_xiaomi
        DeviceVendor.OPPO, DeviceVendor.ONEPLUS, DeviceVendor.REALME -> R.string.accessibility_list_oppo
        DeviceVendor.VIVO -> R.string.accessibility_list_vivo
        DeviceVendor.HUAWEI, DeviceVendor.HONOR -> R.string.accessibility_list_huawei
        DeviceVendor.SAMSUNG -> R.string.accessibility_list_samsung
        DeviceVendor.AOSP -> R.string.accessibility_list_android
    }

    @StringRes
    fun getServiceListAliasRes(vendor: DeviceVendor = currentVendor): Int = when (vendor) {
        DeviceVendor.XIAOMI, DeviceVendor.VIVO -> R.string.accessibility_alias_services
        DeviceVendor.OPPO, DeviceVendor.ONEPLUS, DeviceVendor.REALME -> R.string.accessibility_alias_general
        DeviceVendor.SAMSUNG -> R.string.accessibility_alias_installed_services
        DeviceVendor.HUAWEI, DeviceVendor.HONOR, DeviceVendor.AOSP -> R.string.accessibility_alias_search
    }

    /**
     * 生成安全焦点高亮 Intent：
     * 以标准的 [Settings.ACTION_ACCESSIBILITY_SETTINGS] 为基底，
     * 叠加 AOSP 标准参数以使支持列表自动滚动并闪烁定位至目标服务。
     */
    fun createAccessibilityIntent(context: Context): Intent {
        val componentName = ComponentName(context, LianyeAccessibilityService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(":settings:fragment_args_key", componentName)
            putExtra("extra_fragment_arg_key", componentName)
            putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", componentName)
                putString("extra_fragment_arg_key", componentName)
            })
        }
    }
}
