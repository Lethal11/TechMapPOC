package com.nagarro.techmappoc.ble

import android.bluetooth.BluetoothDevice
import com.nagarro.techmappoc.model.ConnectionState
import com.nagarro.techmappoc.model.PassportData
import com.nagarro.techmappoc.model.PassportStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining BLE operations for passport reader devices.
 * Note: This interface does NOT include connect() and disconnect() methods
 * because they conflict with Nordic BLE's methods that return ConnectRequest/DisconnectRequest.
 * Instead, use connectToDevice() and disconnectFromDevice() for our custom operations.
 */
interface BleManager {

    /**
     * Current BLE connection state
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Current passport scanning/reading status
     */
    val passportStatus: StateFlow<PassportStatus>

    /**
     * Parsed passport data (null if no data available)
     */
    val passportData: StateFlow<PassportData?>

    /**
     * Connect to a BLE device
     * Note: Different name to avoid conflict with Nordic's connect() method
     * @param device The Bluetooth device to connect to
     */
    fun connectToDevice(device: BluetoothDevice)

    /**
     * Disconnect from the currently connected device
     * Note: Different name to avoid conflict with Nordic's disconnect() method
     */
    fun disconnectFromDevice()

    /**
     * Start scanning for a passport on the reader
     */
    fun startPassportScan()

    /**
     * Stop the passport scanning process
     */
    fun stopPassportScan()

    /**
     * Request passport data from the reader
     */
    fun getPassportData()

    /**
     * Reset the reader to initial state
     */
    fun resetReader()
}
