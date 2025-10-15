package com.nagarro.techmappoc.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nagarro.techmappoc.ble.PassportBleManager
import com.nagarro.techmappoc.repository.BleRepository

class PassportReaderViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PassportReaderViewModel::class.java)) {
            val bleManager = PassportBleManager(context.applicationContext)
            val bleRepository = BleRepository(context.applicationContext)
            return PassportReaderViewModel(bleManager, bleRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
