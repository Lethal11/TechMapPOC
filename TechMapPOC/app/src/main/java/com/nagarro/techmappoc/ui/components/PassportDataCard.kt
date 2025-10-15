package com.nagarro.techmappoc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nagarro.techmappoc.model.PassportData

@Composable
fun PassportDataCard(
    data: PassportData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Passport Information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            DataRow(label = "Document Number", value = data.documentNumber)
            DataRow(label = "Surname", value = data.surname)
            DataRow(label = "Given Names", value = data.givenNames)
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            
            DataRow(label = "Nationality", value = data.nationality)
            DataRow(label = "Date of Birth", value = formatDate(data.dateOfBirth))
            DataRow(label = "Sex", value = data.sex)
            DataRow(label = "Expiry Date", value = formatDate(data.expiryDate))
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            
            DataRow(label = "UID", value = data.uid.joinToString(" ") { "%02X".format(it) })
            DataRow(
                label = "Photo Available",
                value = if (data.photoAvailable) "Yes" else "No"
            )
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(140.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun formatDate(date: String): String {
    return if (date.length == 8) {
        "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
    } else {
        date
    }
}
