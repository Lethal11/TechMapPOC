package com.nagarro.techmappoc.repository

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.nagarro.techmappoc.model.BleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BleRepository(private val context: Context) {

    companion object {
        private const val TAG = "BleRepository"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    /**
     * Scan for BLE devices with optional name filter
     * Returns a Flow that emits updated device lists as devices are discovered
     *
     * @param nameFilter Optional filter to only include devices with names containing this string
     * @return Flow of device lists
     */
    fun scanForDevices(nameFilter: String? = null): Flow<List<BleDevice>> = callbackFlow {
        val devices = mutableMapOf<String, BleDevice>()
        var scanResultCount = 0

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scanResultCount++
                Log.d(TAG, "onScanResult called (count: $scanResultCount)")

                try {
                    // Check permission before accessing device name
                    val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(
                                this@BleRepository.context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            result.device.name
                        } else {
                            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, can't get device name")
                            null
                        }
                    } else {
                        try {
                            result.device.name
                        } catch (e: SecurityException) {
                            Log.e(TAG, "SecurityException getting device name", e)
                            null
                        }
                    }

                    Log.d(TAG, "Device found: name='$deviceName', address=${result.device.address}, rssi=${result.rssi}")

                    // Apply filter if provided
                    if (nameFilter != null) {
                        if (deviceName?.contains(nameFilter, ignoreCase = true) == true) {
                            Log.d(TAG, "Device matches filter '$nameFilter'")
                        } else {
                            Log.d(TAG, "Device does NOT match filter '$nameFilter', skipping")
                            return
                        }
                    }

                    val bleDevice = BleDevice(
                        device = result.device,
                        name = deviceName,
                        address = result.device.address,
                        rssi = result.rssi
                    )

                    devices[result.device.address] = bleDevice
                    val sendResult = trySend(devices.values.toList())
                    Log.d(TAG, "Emitted ${devices.size} device(s), send success: ${sendResult.isSuccess}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing scan result", e)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> Log.e(TAG, "SCAN_FAILED_ALREADY_STARTED")
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED")
                    SCAN_FAILED_INTERNAL_ERROR -> Log.e(TAG, "SCAN_FAILED_INTERNAL_ERROR")
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED")
                    else -> Log.e(TAG, "Unknown scan failure code: $errorCode")
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Log.d(TAG, "onBatchScanResults: ${results.size} results")
                super.onBatchScanResults(results)
            }
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "Bluetooth scanner not available")
            close()
            return@callbackFlow
        }

        try {
            // Check scan permission before starting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this@BleRepository.context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

                Log.d(TAG, "BLUETOOTH_SCAN permission: ${if (hasPermission) "GRANTED" else "DENIED"}")

                if (!hasPermission) {
                    Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
                    close()
                    return@callbackFlow
                }
            }

            Log.d(TAG, "Starting BLE scan with filter: ${nameFilter ?: "none"}")
            scanner.startScan(scanCallback)
            Log.d(TAG, "BLE scan started successfully")

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when starting scan - missing permissions?", e)
            close()
            return@callbackFlow
        } catch (e: Exception) {
            Log.e(TAG, "Exception when starting scan", e)
            close()
            return@callbackFlow
        }

        awaitClose {
            Log.d(TAG, "awaitClose called, stopping scan (found $scanResultCount total results)")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(
                            this@BleRepository.context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        scanner.stopScan(scanCallback)
                    }
                } else {
                    scanner.stopScan(scanCallback)
                }
                Log.d(TAG, "BLE scan stopped")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when stopping scan", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception when stopping scan", e)
            }
        }
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Check if Bluetooth adapter is available
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }
}