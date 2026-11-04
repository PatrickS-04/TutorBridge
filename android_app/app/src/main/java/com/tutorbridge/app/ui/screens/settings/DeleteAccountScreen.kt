package com.tutorbridge.app.ui.screens.settings

import androidx.compose.ui.res.stringResource
import com.tutorbridge.app.R
import androidx.compose.ui.res.pluralStringResource
import com.tutorbridge.app.ui.components.NeedsConnectionHint
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val canDelete = uiState.deleteConfirmChecked && uiState.deleteConfirmText.trim().equals(stringResource(R.string.delete_account_confirm_word), ignoreCase = false)

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
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    stringResource(R.string.delete_account_delete_student_account),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.delete_account_permanent_action_warning), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        stringResource(R.string.delete_account_deleting_your_student_account_is),
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
                    Text(stringResource(R.string.delete_account_data_to_be_purged), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PurgeBullet(stringResource(R.string.delete_account_bullet_profile, uiState.userName, uiState.userEmail))
                    PurgeBullet(stringResource(R.string.delete_account_bullet_requests, uiState.myRequests.size))
                    PurgeBullet(stringResource(R.string.delete_account_upcoming_confirmed_campus_room_b))
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
                    Text(stringResource(R.string.delete_account_reason_for_leaving), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = uiState.deleteAccountReason,
                        onValueChange = onReasonChange,
                        placeholder = { Text(stringResource(R.string.delete_account_graduating_or_found_an_external))},
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
                        stringResource(R.string.delete_account_i_understand_that_this_action),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text(stringResource(R.string.delete_account_type_delete_to_confirm), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = uiState.deleteConfirmText,
                    onValueChange = onConfirmTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.common_delete)) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        item {
            uiState.authErrorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Button(
                onClick = onConfirmDelete,
                enabled = canDelete && !uiState.isAuthLoading && uiState.isOnline,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (uiState.isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_account_permanently_delete_account), fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            NeedsConnectionHint(uiState.isOnline)
            if (uiState.pendingSyncCount + uiState.failedSyncCount > 0) {
                val unsent = uiState.pendingSyncCount + uiState.failedSyncCount
                Text(
                    pluralStringResource(R.plurals.delete_unsent_warning, unsent, unsent),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete_account_cancel_keep_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PurgeBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
