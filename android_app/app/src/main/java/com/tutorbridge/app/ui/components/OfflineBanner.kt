package com.tutorbridge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeToday = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val timeOtherDay = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())

/** "14:05" if it was saved today, "3 Oct 14:05" if not - so old data is never mistaken for fresh. */
internal fun savedAtLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return moment.format(if (moment.toLocalDate() == today) timeToday else timeOtherDay)
}

/**
 * A strip across the top of every screen while the app is offline. It says why
 * (no connection, or offline mode switched on) and, once there is an outbox,
 * how many things are waiting to be sent. Draws nothing when online.
 */
@Composable
fun OfflineBanner(uiState: TutorBridgeUiState, modifier: Modifier = Modifier) {
    if (uiState.isOnline) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(if (uiState.isOffline) R.string.offline_banner_forced else R.string.offline_banner_network),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        uiState.cacheSavedAtMillis?.let { savedAt ->
            Text(
                text = stringResource(R.string.offline_showing_saved, savedAtLabel(savedAt)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 26.dp)
            )
        }
        if (uiState.pendingSyncCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.pending_sync, uiState.pendingSyncCount, uiState.pendingSyncCount),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 26.dp)
            )
        }
    }
}
