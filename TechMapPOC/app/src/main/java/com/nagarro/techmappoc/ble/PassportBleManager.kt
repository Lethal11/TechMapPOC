package com.nagarro.techmappoc.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.nagarro.techmappoc.model.ConnectionState
import com.nagarro.techmappoc.model.PassportData
import com.nagarro.techmappoc.model.PassportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import java.util.UUID

/**
 * BLE Manager implementation for passport reader devices using Nordic BLE Library.
 * This class extends Nordic's BleManager and implements our custom BleManager interface.
 */
class PassportBleManager(context: Context) :
    no.nordicsemi.android.ble.BleManager(context),
    BleManager {

    companion object {
        private const val TAG = "PassportBleManager"

        // ============================================================
        // UUIDs from firmware: ble_passport_service.h
        // ============================================================

        // Service UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
        private val PASSPORT_SERVICE_UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

        // Status Characteristic UUID: 6E400002-B5A3-F393-E0A9-E50E24DCCA9E
        // Properties: NOTIFY (firmware sends status updates to Android)
        private val STATUS_CHARACTERISTIC_UUID =
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

        // Data Characteristic UUID: 6E400003-B5A3-F393-E0A9-E50E24DCCA9E
        // Properties: NOTIFY (firmware sends passport data to Android)
        private val DATA_CHARACTERISTIC_UUID =
            UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        // Control Characteristic UUID: 6E400004-B5A3-F393-E0A9-E50E24DCCA9E
        // Properties: WRITE (Android sends commands to firmware)
        private val COMMAND_CHARACTERISTIC_UUID =
            UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")

        // ============================================================
        // Command bytes (matches firmware passport_command_t)
        // ============================================================
        private const val CMD_START_SCAN: Byte = 0x01
        private const val CMD_STOP_SCAN: Byte = 0x02
        private const val CMD_GET_DATA: Byte = 0x03
        private const val CMD_RESET: Byte = 0x04
    }

    // GATT Characteristics
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null

    // State flows for reactive UI updates
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _passportStatus = MutableStateFlow(PassportStatus.IDLE)
    override val passportStatus: StateFlow<PassportStatus> = _passportStatus.asStateFlow()

    private val _passportData = MutableStateFlow<PassportData?>(null)
    override val passportData: StateFlow<PassportData?> = _passportData.asStateFlow()

    // Callbacks for BLE notifications
    private val statusCallback = DataReceivedCallback { _, data ->
        data.value?.let { bytes ->
            if (bytes.isNotEmpty()) {
                handleStatusUpdate(bytes[0])
            }
        }
    }

    private val dataCallback = DataReceivedCallback { _, data ->
        data?.value?.let { bytes ->
            handlePassportData(bytes)
        }
    }

    // ========================================
    // Nordic BLE Manager REQUIRED OVERRIDES
    // ========================================

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    override fun getMinLogPriority(): Int = Log.VERBOSE

    override fun getGattCallback(): BleManagerGattCallback = PassportGattCallback()

    // ========================================
    // LIFECYCLE CALLBACKS
    // ========================================

    /**
     * Called when device is fully ready (connected, services discovered, initialized)
     */
    override fun onDeviceReady() {
        super.onDeviceReady()
        _connectionState.value = ConnectionState.CONNECTED
        Log.d(TAG, "Device is ready for communication")
    }

    /**
     * Called when device is disconnected
     */
    override fun shouldClearCacheWhenDisconnected(): Boolean {
        super.shouldClearCacheWhenDisconnected()
        _connectionState.value = ConnectionState.DISCONNECTED
        _passportStatus.value = PassportStatus.IDLE
        _passportData.value = null
        return true
    }

    // ========================================
    // CUSTOM INTERFACE IMPLEMENTATION
    // ========================================

    /**
     * Connect to a BLE device
     * Note: Uses different method name (connectToDevice) to avoid conflict with Nordic's connect()
     */
    @SuppressLint("MissingPermission")
    override fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "Connecting to device: ${device.name} (${device.address})")

        // Call Nordic's connect() method which returns ConnectRequest
        connect(device)
            .useAutoConnect(false)
            .retry(3, 100)
            .enqueue()
    }

    /**
     * Disconnect from the currently connected device
     * Note: Uses different method name (disconnectFromDevice) to avoid conflict with Nordic's disconnect()
     */
    override fun disconnectFromDevice() {
        _connectionState.value = ConnectionState.DISCONNECTING
        Log.d(TAG, "Disconnecting from device")

        // Call Nordic's disconnect() method which returns DisconnectRequest
        disconnect().enqueue()
    }

    override fun startPassportScan() {
        sendCommand(CMD_START_SCAN)
    }

    override fun stopPassportScan() {
        sendCommand(CMD_STOP_SCAN)
    }

    override fun getPassportData() {
        sendCommand(CMD_GET_DATA)
    }

    override fun resetReader() {
        sendCommand(CMD_RESET)
        _passportData.value = null
        _passportStatus.value = PassportStatus.IDLE
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private fun sendCommand(command: Byte) {
        commandCharacteristic?.let { characteristic ->
            writeCharacteristic(characteristic, byteArrayOf(command))
                .enqueue()
        } ?: run {
            Log.e(TAG, "Command characteristic not initialized")
        }
    }

    private fun handleStatusUpdate(statusByte: Byte) {
        _passportStatus.value = when (statusByte.toInt()) {
            0 -> PassportStatus.IDLE
            1 -> PassportStatus.SCANNING
            2 -> PassportStatus.NO_CARD
            3 -> PassportStatus.CARD_DETECTED
            4 -> PassportStatus.READING
            5 -> PassportStatus.DATA_READ
            else -> PassportStatus.ERROR
        }
        Log.d(TAG, "Status updated: ${_passportStatus.value}")
    }

    private fun handlePassportData(bytes: ByteArray) {
        try {
            val data = String(bytes, Charsets.UTF_8)
            val parts = data.split("|")

            if (parts.size >= 8) {
                _passportData.value = PassportData(
                    documentNumber = parts[0],
                    surname = parts[1],
                    givenNames = parts[2],
                    nationality = parts[3],
                    dateOfBirth = parts[4],
                    sex = parts[5],
                    expiryDate = parts[6],
                    uid = parts[7].toByteArray(),
                    photoAvailable = parts.getOrNull(8)?.toBoolean() ?: false
                )
                Log.d(TAG, "Passport data received and parsed")
            } else {
                Log.e(TAG, "Invalid passport data format: expected 8+ parts, got ${parts.size}")
                _passportStatus.value = PassportStatus.ERROR
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing passport data", e)
            _passportStatus.value = PassportStatus.ERROR
        }
    }

    // ========================================
    // GATT CALLBACK
    // ========================================

    private inner class PassportGattCallback : BleManagerGattCallback() {

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            Log.d(TAG, "Checking for required services...")

            val service = gatt.getService(PASSPORT_SERVICE_UUID)
            if (service != null) {
                commandCharacteristic = service.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
                statusCharacteristic = service.getCharacteristic(STATUS_CHARACTERISTIC_UUID)
                dataCharacteristic = service.getCharacteristic(DATA_CHARACTERISTIC_UUID)
            }

            val supported = commandCharacteristic != null &&
                    statusCharacteristic != null &&
                    dataCharacteristic != null

            if (!supported) {
                Log.e(TAG, "Required service not found or characteristics missing")
                Log.e(TAG, "Service UUID: $PASSPORT_SERVICE_UUID")
                Log.e(TAG, "Command char: ${commandCharacteristic != null}")
                Log.e(TAG, "Status char: ${statusCharacteristic != null}")
                Log.e(TAG, "Data char: ${dataCharacteristic != null}")
            }

            return supported
        }

        override fun initialize() {
            super.initialize()
            Log.d(TAG, "Initializing device...")

            // Enable status notifications
            statusCharacteristic?.let { characteristic ->
                setNotificationCallback(characteristic).with(statusCallback)
                enableNotifications(characteristic).enqueue()
            }

            // Enable data notifications
            dataCharacteristic?.let { characteristic ->
                setNotificationCallback(characteristic).with(dataCallback)
                enableNotifications(characteristic).enqueue()
            }

            Log.d(TAG, "Initialization complete")
        }

        override fun onServicesInvalidated() {
            Log.d(TAG, "Services invalidated, clearing characteristics")
            commandCharacteristic = null
            statusCharacteristic = null
            dataCharacteristic = null
        }
    }
}