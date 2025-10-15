package com.nagarro.techmappoc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nagarro.techmappoc.model.BleDevice

@Composable
fun DeviceItem(
    device: BleDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Bluetooth Device",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = getSignalStrength(device.rssi),
                    style = MaterialTheme.typography.labelSmall,
                    color = getSignalColor(device.rssi)
                )
            }
        }
    }
}

@Composable
private fun getSignalColor(rssi: Int): androidx.compose.ui.graphics.Color {
    return when {
        rssi >= -60 -> MaterialTheme.colorScheme.tertiary
        rssi >= -75 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
}

private fun getSignalStrength(rssi: Int): String {
    return when {
        rssi >= -60 -> "Excellent"
        rssi >= -75 -> "Good"
        rssi >= -85 -> "Fair"
        else -> "Weak"
    }
}
