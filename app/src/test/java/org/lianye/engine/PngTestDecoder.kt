package org.lianye.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream

/** Independent PNG decoder for the Android JVM test classpath (which excludes java.desktop). */
internal data class TestPng(val width: Int, val height: Int, private val rgba: ByteArray) {
    fun getRGB(x: Int, y: Int): Int {
        val offset = (y * width + x) * 4
        return ((rgba[offset + 3].toInt() and 255) shl 24) or
            ((rgba[offset].toInt() and 255) shl 16) or
            ((rgba[offset + 1].toInt() and 255) shl 8) or (rgba[offset + 2].toInt() and 255)
    }
}

internal fun decodeTestPng(bytes: ByteArray): TestPng {
    var width = 0
    var height = 0
    val idat = ByteArrayOutputStream()
    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val signature = ByteArray(8).also { input.readFully(it) }
        check(signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        var ended = false
        while (!ended) {
            val length = input.readInt()
            check(length >= 0 && length <= bytes.size)
            val type = ByteArray(4).also { input.readFully(it) }
            val payload = ByteArray(length).also { input.readFully(it) }
            val checksum = input.readInt().toLong() and 0xFFFFFFFFL
            check(CRC32().apply { update(type); update(payload) }.value == checksum) { "Invalid chunk CRC" }
            when (String(type, Charsets.US_ASCII)) {
                "IHDR" -> {
                    check(width == 0 && length == 13)
                    DataInputStream(ByteArrayInputStream(payload)).use { header ->
                        width = header.readInt()
                        height = header.readInt()
                        check(width > 0 && height > 0)
                        check(header.readUnsignedByte() == 8 && header.readUnsignedByte() == 6)
                        repeat(3) { check(header.readUnsignedByte() == 0) }
                    }
                }
                "IDAT" -> idat.write(payload)
                "IEND" -> { check(length == 0); ended = true }
            }
        }
        check(input.read() == -1)
    }
    val rgba = ByteArray(width * height * 4)
    DataInputStream(InflaterInputStream(ByteArrayInputStream(idat.toByteArray()))).use { input ->
        for (y in 0 until height) {
            check(input.readUnsignedByte() == 0) { "Expected independent unfiltered scanline" }
            input.readFully(rgba, y * width * 4, width * 4)
        }
        check(input.read() == -1)
    }
    return TestPng(width, height, rgba)
}
