package com.nagarro.techmappoc.util

/**
 * Format a date string from YYYYMMDD to YYYY-MM-DD
 */
fun String.formatPassportDate(): String {
    return if (this.length == 8) {
        "${this.substring(0, 4)}-${this.substring(4, 6)}-${this.substring(6, 8)}"
    } else {
        this
    }
}

/**
 * Format a byte array to hex string with spaces
 */
fun ByteArray.toHexString(): String {
    return this.joinToString(" ") { "%02X".format(it) }
}

/**
 * Parse hex string to byte array
 */
fun String.hexToByteArray(): ByteArray {
    val cleaned = this.replace(" ", "").replace("-", "")
    return ByteArray(cleaned.length / 2) {
        cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
