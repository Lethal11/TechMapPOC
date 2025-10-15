package com.nagarro.techmappoc.model

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Int
)
