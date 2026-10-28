package com.tutorbridge.app.ui.screens.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R
import com.tutorbridge.app.data.local.OutboxItemUi
import com.tutorbridge.app.data.local.OutboxType
import com.tutorbridge.app.data.local.toItemUis
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

/**
 * Everything the user did that has not reached the server yet, and what became
 * of anything the server refused. This is where the banner's "3 changes waiting"
 * and "1 change couldn't be sent" lead: so nothing queued is a mystery, and
 * nothing refused is stuck without a way out - it can be retried or discarded.
 */
@Composable
fun SyncStatusScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onRetryFailed: () -> Unit,
    onDiscard: (Long) -> Unit
) {
    val items = uiState.outboxItems.toItemUis()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
            Text(stringResource(R.string.sync_status_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.sync_all_clear), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            return@Column
        }

        // The state of play, in the same words the banner uses.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (uiState.pendingSyncCount > 0) {
                Text(pluralStringResource(R.plurals.pending_sync, uiState.pendingSyncCount, uiState.pendingSyncCount), fontSize = 13.sp)
            }
            if (uiState.failedSyncCount > 0) {
                Text(
                    pluralStringResource(R.plurals.failed_sync, uiState.failedSyncCount, uiState.failedSyncCount),
                    fontSize = 13.sp,
                    color = CrimsonPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!uiState.isOnline) {
                Text(
                    stringResource(if (uiState.isOffline) R.string.offline_banner_forced else R.string.offline_banner_network),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSyncNow,
                enabled = uiState.isOnline && uiState.pendingSyncCount > 0,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
            ) { Text(stringResource(R.string.sync_now), fontWeight = FontWeight.Bold, fontSize = 12.sp) }

            OutlinedButton(
                onClick = onRetryFailed,
                enabled = uiState.isOnline && uiState.failedSyncCount > 0,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp)
            ) { Text(stringResource(R.string.retry_failed), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item -> ItemCard(item, onDiscard) }
        }
    }
}

@Composable
private fun ItemCard(item: OutboxItemUi, onDiscard: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(kindLabel(item.type), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Badge(containerColor = if (item.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        stringResource(if (item.failed) R.string.sync_state_failed else R.string.waiting_to_send),
                        fontSize = 12.sp,
                        color = if (item.failed) CrimsonPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.preview.isNotBlank()) {
                Text(item.preview, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // The server's own reason, so a refusal is explained rather than just announced.
            if (item.failed && !item.error.isNullOrBlank()) {
                Text(item.error, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.failed) {
                TextButton(onClick = { onDiscard(item.id) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.discard), color = CrimsonPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun kindLabel(type: OutboxType?): String = when (type) {
    OutboxType.SEND_MESSAGE -> stringResource(R.string.sync_kind_message)
    OutboxType.CREATE_REQUEST -> stringResource(R.string.sync_kind_request)
    OutboxType.SAVE_NOTES -> stringResource(R.string.sync_kind_notes)
    OutboxType.SAVE_SETTINGS -> stringResource(R.string.sync_kind_settings)
    null -> ""
}

@Preview(showBackground = true)
@Composable
fun SyncStatusScreenPreview() {
    TutorBridgeTheme {
        SyncStatusScreen(uiState = TutorBridgeUiState(), onBack = {}, onSyncNow = {}, onRetryFailed = {}, onDiscard = {})
    }
}
