package com.nagarro.techmappoc.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nagarro.techmappoc.model.ConnectionState
import com.nagarro.techmappoc.ui.components.*
import com.nagarro.techmappoc.ui.viewmodel.PassportReaderViewModel
import com.nagarro.techmappoc.ui.viewmodel.PassportReaderViewModelFactory

@Composable
fun PassportReaderScreen(
    viewModel: PassportReaderViewModel = viewModel(
        factory = PassportReaderViewModelFactory(LocalContext.current)
    )
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val passportStatus by viewModel.passportStatus.collectAsState()
    val passportData by viewModel.passportData.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "TechPassport Reader",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Connection status card
            ConnectionStatusCard(
                connectionState = connectionState,
                passportStatus = passportStatus
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content based on connection state
            when (connectionState) {
                ConnectionState.DISCONNECTED -> {
                    DeviceListSection(
                        devices = devices,
                        isScanning = isScanning,
                        onScanClick = { viewModel.startDeviceScan() },
                        onDeviceClick = { viewModel.connectToDevice(it) }
                    )
                }
                ConnectionState.CONNECTED -> {
                    PassportControlSection(
                        passportStatus = passportStatus,
                        passportData = passportData,
                        onStartScan = { viewModel.startPassportScan() },
                        onStopScan = { viewModel.stopPassportScan() },
                        onGetData = { viewModel.getPassportData() },
                        onReset = { viewModel.resetReader() },
                        onDisconnect = { viewModel.disconnect() }
                    )
                }
                ConnectionState.CONNECTING,
                ConnectionState.DISCONNECTING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTING -> "Connecting..."
                                    ConnectionState.DISCONNECTING -> "Disconnecting..."
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
