package com.tutorbridge.app.ui.screens.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R
import com.tutorbridge.app.data.model.MessageUi
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import com.tutorbridge.app.viewmodel.messagesByDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One conversation: messages oldest to newest under day headers, with the compose box pinned below. */
@OptIn(ExperimentalFoundationApi::class) // stickyHeader
@Composable
fun ChatScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadEarlier: () -> Unit
) {
    val listState = rememberLazyListState()
    val hasEarlier = uiState.chatPage < uiState.chatTotalPages

    val chat = uiState.visibleChat
    val days = remember(chat) { messagesByDay(chat) }

    // Follow the conversation down when a message arrives or is sent.
    LaunchedEffect(chat.lastOrNull()?.id) {
        if (chat.isNotEmpty()) {
            val above = days.keys.count { it != null } + if (hasEarlier) 1 else 0
            listState.animateScrollToItem(chat.size - 1 + above)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(uiState.selectedThreadName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasEarlier) {
                item {
                    TextButton(onClick = onLoadEarlier, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.chat_load_earlier_messages), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (uiState.visibleChat.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.chat_no_messages_yet_say_hello),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            days.forEach { (day, messages) ->
                if (day != null) stickyHeader(key = "day-$day") { DayHeader(day) }
                items(messages, key = { it.id }) { message -> Bubble(message) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.chatDraft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.chat_message)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            FilledIconButton(
                onClick = onSend,
                enabled = uiState.chatDraft.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

/** The day chip that sits above a day's messages and stays pinned to the top while they scroll by. */
@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val label = when {
        day == today -> stringResource(R.string.chat_today)
        day == today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        day.isAfter(today.minusDays(7)) -> day.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
        else -> day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
    }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun Bubble(message: MessageUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.mine) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                message.body,
                fontSize = 14.sp,
                color = if (message.mine) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                when {
                    message.pending && message.queued -> stringResource(R.string.waiting_to_send)
                    message.pending -> stringResource(R.string.sending)
                    else -> message.time
                },
                fontSize = 12.sp,
                color = if (message.mine) MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    TutorBridgeTheme {
        ChatScreen(
            uiState = TutorBridgeUiState(
                selectedThreadName = "Naledi Dlamini",
                chatMessages = listOf(
                    MessageUi("1", "Can we move to 3pm?", mine = true, time = "14:05", sentAt = "2026-10-07T12:05:00Z"),
                    MessageUi("2", "Sure, see you then.", mine = false, time = "14:07", sentAt = "2026-10-07T12:07:00Z")
                )
            ),
            onBack = {},
            onDraftChange = {},
            onSend = {},
            onLoadEarlier = {}
        )
    }
}
