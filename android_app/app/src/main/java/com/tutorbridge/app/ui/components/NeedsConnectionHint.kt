package com.tutorbridge.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R

/**
 * A line under a button that is greyed out because the action needs the server:
 * booking, confirming, accepting and the like are decided by the server (is the
 * slot free, is the request still open), so they cannot be queued and replayed
 * later the way a message can. Saying why beats a button that silently does nothing.
 * Draws nothing when online.
 */
@Composable
fun NeedsConnectionHint(online: Boolean, modifier: Modifier = Modifier) {
    if (online) return
    Text(
        text = stringResource(R.string.needs_connection),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp)
    )
}
