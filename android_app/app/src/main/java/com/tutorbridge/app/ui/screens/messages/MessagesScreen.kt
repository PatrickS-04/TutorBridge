package com.tutorbridge.app.ui.screens.messages

import com.tutorbridge.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.ThreadUi
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

/** Every conversation the signed-in user is in, most recent first. */
@Composable
fun MessagesScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onOpenThread: (ThreadUi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.common_messages), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (uiState.threads.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.messages_no_conversations_yet), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.messages_a_chat_opens_when_you),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(uiState.threads, key = { it.id }) { thread ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenThread(thread) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text(thread.initials, fontWeight = FontWeight.Bold) }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(thread.otherName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                thread.preview.ifBlank { stringResource(R.string.messages_say_hello) },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (thread.unread > 0) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(thread.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (thread.unread > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Badge(containerColor = CrimsonPrimary) { Text("${thread.unread}", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MessagesScreenPreview() {
    TutorBridgeTheme {
        MessagesScreen(uiState = TutorBridgeUiState(), onBack = {}, onOpenThread = {})
    }
}
