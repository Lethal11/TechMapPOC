package com.nagarro.techmappoc.util

object BleConstants {
    // Scan Settings
    const val SCAN_DURATION_MS = 10000L
    const val DEVICE_NAME_FILTER = "Passport"
    
    // Connection Settings
    const val CONNECTION_RETRY_COUNT = 3
    const val CONNECTION_RETRY_DELAY_MS = 100L
    
    // Timeout Settings
    const val READ_TIMEOUT_MS = 5000L
    const val WRITE_TIMEOUT_MS = 3000L
    
    // Command Bytes
    object Commands {
        const val START_SCAN: Byte = 0x01
        const val STOP_SCAN: Byte = 0x02
        const val GET_DATA: Byte = 0x03
        const val RESET: Byte = 0x04
    }
    
    // Status Bytes
    object Status {
        const val IDLE: Byte = 0x00
        const val SCANNING: Byte = 0x01
        const val NO_CARD: Byte = 0x02
        const val CARD_DETECTED: Byte = 0x03
        const val READING: Byte = 0x04
        const val DATA_READ: Byte = 0x05
        const val ERROR: Byte = 0xFF.toByte()
    }
}
