package com.tutorbridge.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun DeleteAccountScreen(
    uiState: TutorBridgeUiState,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit,
    onReasonChange: (String) -> Unit = {},
    onConfirmCheckChange: (Boolean) -> Unit = {},
    onConfirmTextChange: (String) -> Unit = {}
) {
    val canDelete = uiState.deleteConfirmChecked && uiState.deleteConfirmText.trim().equals("DELETE", ignoreCase = false)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Delete Student Account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonPrimary
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(18.dp))
                        Text("Permanent Action Warning", fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                    }
                    Text(
                        "Deleting your student account is irreversible. All your pending tuition requests, upcoming campus consultation room bookings and study materials will be deleted immediately.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DATA TO BE PURGED", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PurgeBullet("Student profile (${uiState.userName} — ${uiState.userEmail})")
                    PurgeBullet("All active and past tuition requests (${uiState.myRequests.size} recorded)")
                    PurgeBullet("Upcoming confirmed campus room bookings and calendar schedules")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("REASON FOR LEAVING", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = uiState.deleteAccountReason,
                        onValueChange = onReasonChange,
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = uiState.deleteConfirmChecked, onCheckedChange = onConfirmCheckChange)
                    Text(
                        "I understand that this action cannot be undone and all my campus tutoring records will be deleted.",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text("Type DELETE to confirm:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = uiState.deleteConfirmText,
                    onValueChange = onConfirmTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("DELETE") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        item {
            Button(
                onClick = onConfirmDelete,
                enabled = canDelete,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Permanently Delete Account", fontWeight = FontWeight.Bold)
            }
        }

        item {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel & Keep Account", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PurgeBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = CrimsonPrimary, fontWeight = FontWeight.Bold)
        Text(text, fontSize = 13.sp)
    }
}

@Preview(showBackground = true)
@Composable
fun DeleteAccountScreenPreview() {
    TutorBridgeTheme {
        DeleteAccountScreen(
            uiState = TutorBridgeUiState(),
            onConfirmDelete = {},
            onCancel = {}
        )
    }
}
