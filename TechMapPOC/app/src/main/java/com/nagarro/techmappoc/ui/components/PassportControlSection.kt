package com.nagarro.techmappoc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nagarro.techmappoc.model.PassportData
import com.nagarro.techmappoc.model.PassportStatus

@Composable
fun PassportControlSection(
    passportStatus: PassportStatus,
    passportData: PassportData?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onGetData: () -> Unit,
    onReset: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Control buttons
        ControlButtons(
            passportStatus = passportStatus,
            passportData = passportData,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onGetData = onGetData,
            onReset = onReset,
            onDisconnect = onDisconnect
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Passport data or status display
        if (passportData != null) {
            PassportDataCard(passportData)
        } else {
            PassportStatusCard(passportStatus)
        }
    }
}

@Composable
private fun ControlButtons(
    passportStatus: PassportStatus,
    passportData: PassportData?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onGetData: () -> Unit,
    onReset: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartScan,
                modifier = Modifier.weight(1f),
                enabled = passportStatus != PassportStatus.SCANNING
            ) {
                Text("Start Scan")
            }

            Button(
                onClick = onStopScan,
                modifier = Modifier.weight(1f),
                enabled = passportStatus == PassportStatus.SCANNING
            ) {
                Text("Stop Scan")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onGetData,
                modifier = Modifier.weight(1f),
                enabled = passportStatus == PassportStatus.CARD_DETECTED || 
                         passportStatus == PassportStatus.DATA_READ
            ) {
                Text("Get Data")
            }

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }
        }

        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Disconnect")
        }
    }
}

@Composable
private fun PassportStatusCard(passportStatus: PassportStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (passportStatus == PassportStatus.SCANNING || 
                    passportStatus == PassportStatus.READING) {
                    CircularProgressIndicator()
                }
                
                Text(
                    text = getStatusMessage(passportStatus),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getStatusMessage(status: PassportStatus): String {
    return when (status) {
        PassportStatus.IDLE -> "Place passport on reader and click 'Start Scan'"
        PassportStatus.SCANNING -> "Scanning for passport..."
        PassportStatus.NO_CARD -> "No card detected. Please place passport on reader."
        PassportStatus.CARD_DETECTED -> "Passport detected! Click 'Get Data' to read."
        PassportStatus.READING -> "Reading passport data..."
        PassportStatus.DATA_READ -> "Data read successfully!"
        PassportStatus.ERROR -> "Error reading passport. Please try again."
    }
}
