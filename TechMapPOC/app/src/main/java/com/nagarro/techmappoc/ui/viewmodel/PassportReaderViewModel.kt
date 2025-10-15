package com.nagarro.techmappoc.ui.viewmodel

import android.content.Context
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nagarro.techmappoc.ble.BleManager
import com.nagarro.techmappoc.model.BleDevice
import com.nagarro.techmappoc.model.ConnectionState
import com.nagarro.techmappoc.model.PassportData
import com.nagarro.techmappoc.model.PassportStatus
import com.nagarro.techmappoc.repository.BleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class PassportReaderViewModel(
    private val bleManager: BleManager,
    private val bleRepository: BleRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PassportReaderViewModel"
        private const val SCAN_DURATION_MS = 10000L
        private const val DEVICE_NAME_FILTER = "Passport"
    }

    // BLE Scanning State
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    // Connection and Passport State from BleManager
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val passportStatus: StateFlow<PassportStatus> = bleManager.passportStatus
    val passportData: StateFlow<PassportData?> = bleManager.passportData

    // Error State
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null

    init {
        Log.d(TAG, "PassportReaderViewModel initialized")
    }

    fun startDeviceScan() {
        if (_isScanning.value) {
            Log.w(TAG, "Scan already in progress")
            return
        }

        if (!bleRepository.isBluetoothAvailable()) {
            _errorMessage.value = "Bluetooth is not available on this device"
            Log.e(TAG, "Bluetooth is not available")
            return
        }

        if (!bleRepository.isBluetoothEnabled()) {
            _errorMessage.value = "Bluetooth is not enabled"
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }

        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        _errorMessage.value = null

        Log.d(TAG, "Starting BLE device scan...")

        // Start the scan
        scanJob = viewModelScope.launch {
            try {
                bleRepository.scanForDevices(DEVICE_NAME_FILTER)
                    .collect { devices ->
                        _discoveredDevices.value = devices
                        Log.d(TAG, "Found ${devices.size} device(s)")
                    }
            } catch (e: CancellationException) {
                // Normal cancellation - don't log as error
                Log.d(TAG, "Scan cancelled")
            } catch (e: Exception) {
                // Real error
                Log.e(TAG, "Scan error", e)
                _errorMessage.value = "Scan error: ${e.message}"
            } finally {
                // Always update scanning state
                _isScanning.value = false
                Log.d(TAG, "Scan stopped")
            }
        }

        // Auto-stop scan after duration
        scanTimeoutJob = viewModelScope.launch {
            try {
                delay(SCAN_DURATION_MS)
                stopDeviceScan()
            } catch (e: CancellationException) {
                // Timeout was cancelled (normal if user stops scan manually)
                Log.d(TAG, "Scan timeout cancelled")
            }
        }
    }

    fun stopDeviceScan() {
        Log.d(TAG, "Stopping device scan...")

        // Cancel the scan job
        scanJob?.cancel()
        scanJob = null

        // Cancel the timeout job (prevents it from calling stopDeviceScan again)
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        _isScanning.value = false
    }

    fun connectToDevice(bleDevice: BleDevice) {
        stopDeviceScan()
        Log.d(TAG, "Connecting to device: ${bleDevice.name} (${bleDevice.address})")
        
        try {
            bleManager.connectToDevice(bleDevice.device)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
            _errorMessage.value = "Connection error: ${e.message}"
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting from device")
        try {
            bleManager.disconnectFromDevice()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
            _errorMessage.value = "Disconnect error: ${e.message}"
        }
    }

    fun startPassportScan() {
        Log.d(TAG, "Starting passport scan")
        try {
            bleManager.startPassportScan()
            _errorMessage.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error starting passport scan", e)
            _errorMessage.value = "Error starting scan: ${e.message}"
        }
    }

    fun stopPassportScan() {
        Log.d(TAG, "Stopping passport scan")
        try {
            bleManager.stopPassportScan()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping passport scan", e)
            _errorMessage.value = "Error stopping scan: ${e.message}"
        }
    }

    fun getPassportData() {
        Log.d(TAG, "Requesting passport data")
        try {
            bleManager.getPassportData()
            _errorMessage.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting passport data", e)
            _errorMessage.value = "Error getting data: ${e.message}"
        }
    }

    fun resetReader() {
        Log.d(TAG, "Resetting reader")
        try {
            bleManager.resetReader()
            _errorMessage.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting reader", e)
            _errorMessage.value = "Error resetting: ${e.message}"
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDeviceScan()
        Log.d(TAG, "ViewModel cleared")
    }

}
