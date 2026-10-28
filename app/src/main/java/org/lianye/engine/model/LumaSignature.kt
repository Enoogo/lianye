package org.lianye.engine.model

data class LumaSignature(
    val height: Int,
    val leftLuma: IntArray,
    val centerLuma: IntArray,
    val rightLuma: IntArray,
    val horizontalVar: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LumaSignature
        if (height != other.height) return false
        if (!leftLuma.contentEquals(other.leftLuma)) return false
        if (!centerLuma.contentEquals(other.centerLuma)) return false
        if (!rightLuma.contentEquals(other.rightLuma)) return false
        return horizontalVar.contentEquals(other.horizontalVar)
    }

    override fun hashCode(): Int {
        var result = height
        result = 31 * result + leftLuma.contentHashCode()
        result = 31 * result + centerLuma.contentHashCode()
        result = 31 * result + rightLuma.contentHashCode()
        result = 31 * result + horizontalVar.contentHashCode()
        return result
    }
}
