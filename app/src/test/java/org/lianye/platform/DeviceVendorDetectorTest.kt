package org.lianye.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lianye.R

class DeviceVendorDetectorTest {

    @Test
    fun resolveVendor_classifiesXiaomiFamilyCorrectly() {
        assertEquals(DeviceVendor.XIAOMI, DeviceVendorDetector.resolveVendor("Xiaomi", "Xiaomi"))
        assertEquals(DeviceVendor.XIAOMI, DeviceVendorDetector.resolveVendor("Redmi", "Redmi"))
        assertEquals(DeviceVendor.XIAOMI, DeviceVendorDetector.resolveVendor("blackshark", "blackshark"))
    }

    @Test
    fun resolveVendor_classifiesOppoFamilyCorrectly() {
        assertEquals(DeviceVendor.OPPO, DeviceVendorDetector.resolveVendor("OPPO", "OPPO"))
        assertEquals(DeviceVendor.ONEPLUS, DeviceVendorDetector.resolveVendor("OnePlus", "OnePlus"))
        assertEquals(DeviceVendor.REALME, DeviceVendorDetector.resolveVendor("realme", "realme"))
    }

    @Test
    fun resolveVendor_classifiesVivoFamilyCorrectly() {
        assertEquals(DeviceVendor.VIVO, DeviceVendorDetector.resolveVendor("vivo", "vivo"))
        assertEquals(DeviceVendor.VIVO, DeviceVendorDetector.resolveVendor("iQOO", "iQOO"))
    }

    @Test
    fun resolveVendor_classifiesHuaweiFamilyCorrectly() {
        assertEquals(DeviceVendor.HUAWEI, DeviceVendorDetector.resolveVendor("HUAWEI", "HUAWEI"))
        assertEquals(DeviceVendor.HONOR, DeviceVendorDetector.resolveVendor("HONOR", "HONOR"))
    }

    @Test fun consumerBrandTakesPriorityOverSharedManufacturer() {
        assertEquals(DeviceVendor.HONOR, DeviceVendorDetector.resolveVendor("HUAWEI", "HONOR"))
        assertEquals(DeviceVendor.ONEPLUS, DeviceVendorDetector.resolveVendor("OPPO", "OnePlus"))
        assertEquals(DeviceVendor.REALME, DeviceVendorDetector.resolveVendor("OPPO", "realme"))
    }

    @Test fun brandOnlyAndMixedCaseDevicesAreRecognized() {
        assertEquals(DeviceVendor.XIAOMI, DeviceVendorDetector.resolveVendor(null, "POCO"))
        assertEquals(DeviceVendor.XIAOMI, DeviceVendorDetector.resolveVendor("unknown", "REDMI"))
        assertEquals(DeviceVendor.VIVO, DeviceVendorDetector.resolveVendor(null, "iQOO"))
        assertEquals(DeviceVendor.SAMSUNG, DeviceVendorDetector.resolveVendor(null, "Samsung"))
        assertEquals(DeviceVendor.HUAWEI, DeviceVendorDetector.resolveVendor(null, " Huawei "))
        assertEquals(DeviceVendor.AOSP, DeviceVendorDetector.resolveVendor(null, null))
    }

    @Test
    fun resolveVendor_classifiesSamsungCorrectly() {
        assertEquals(DeviceVendor.SAMSUNG, DeviceVendorDetector.resolveVendor("samsung", "samsung"))
    }

    @Test
    fun resolveVendor_classifiesOthersAsAosp() {
        assertEquals(DeviceVendor.AOSP, DeviceVendorDetector.resolveVendor("Google", "Pixel"))
        assertEquals(DeviceVendor.AOSP, DeviceVendorDetector.resolveVendor("Sony", "Xperia"))
        assertEquals(DeviceVendor.AOSP, DeviceVendorDetector.resolveVendor("", ""))
    }

    @Test
    fun getPreFlightHintRes_returnsValidStringResourceForEachVendor() {
        DeviceVendor.entries.forEach { vendor ->
            val resId = DeviceVendorDetector.getPreFlightHintRes(vendor)
            assertTrue("Resource ID for $vendor must be greater than 0", resId > 0)
        }
    }
}
