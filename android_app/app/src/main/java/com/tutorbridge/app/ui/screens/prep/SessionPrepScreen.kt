package com.tutorbridge.app.ui.screens.prep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

/**
 * The tutor's own notes for one session: a checklist of what has to be done,
 * and a box for anything else worth remembering about this student or guardian.
 *
 * Ticking, adding and removing a row saves immediately. The side notes are a
 * draft until [onSaveNotes], which writes them and returns to the calendar -
 * or until [onBack], which writes them too, so nothing typed is lost by
 * walking away.
 */
@Composable
fun SessionPrepScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onToggleGoal: (String) -> Unit = {},
    onRemoveGoal: (String) -> Unit = {},
    onNewItemChange: (String) -> Unit = {},
    onAddItem: () -> Unit = {},
    onSideNotesChange: (String) -> Unit = {},
    onSaveNotes: () -> Unit = {}
) {
    val session = uiState.selectedSession
    val checklist = uiState.sessionChecklist
    val completed = checklist.count { it.completed }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Session Notes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Who the session is with, and when.
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavySecondary)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                initialsOf(session?.participantName),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Column {
                            Text(
                                session?.participantName ?: "This session",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                listOfNotNull(
                                    session?.subject?.takeIf { it.isNotBlank() },
                                    session?.date,
                                    session?.startTime?.takeIf { it.isNotBlank() }
                                ).joinToString(" • "),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    val venue = session?.location.orEmpty()
                    if (venue.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = "Venue",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(venue, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ===== The checklist =====
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("To do for this session", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (checklist.isNotEmpty()) {
                    Text(
                        "$completed/${checklist.size} done",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (checklist.isEmpty()) {
                        Text(
                            "Nothing on the list yet. Add what has to be done before or during the session.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }

                    checklist.forEach { goal ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = goal.completed, onCheckedChange = { onToggleGoal(goal.id) })
                            Text(
                                goal.text,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                textDecoration = if (goal.completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (goal.completed) CrimsonPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { onRemoveGoal(goal.id) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove \"${goal.text}\"",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = uiState.newChecklistItem,
                        onValueChange = onNewItemChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Add a to-do", fontSize = 13.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onAddItem() }),
                        trailingIcon = {
                            IconButton(
                                onClick = onAddItem,
                                enabled = uiState.newChecklistItem.isNotBlank()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add to-do", tint = CrimsonPrimary)
                            }
                        }
                    )
                }
            }
        }

        // ===== Free-text side notes =====
        item {
            Text("Side notes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            OutlinedTextField(
                value = uiState.sessionSideNotes,
                onValueChange = onSideNotesChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                placeholder = {
                    Text("Anything worth remembering about this student or guardian", fontSize = 13.sp)
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                supportingText = { Text("Only you can see these notes.", fontSize = 11.sp) }
            )
        }

        item {
            // Always enabled: this is also the way out of the screen, and a
            // tutor who only ticked boxes should not find it greyed out.
            Button(
                onClick = onSaveNotes,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save notes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/** "Liam Naidoo" -> "LN"; blank falls back to a neutral mark. */
private fun initialsOf(name: String?): String =
    name?.trim()?.split(" ")?.filter { it.isNotBlank() }?.take(2)
        ?.joinToString("") { it.first().uppercase() }
        ?.ifBlank { "?" } ?: "?"

@Preview(showBackground = true)
@Composable
fun SessionPrepScreenPreview() {
    TutorBridgeTheme {
        SessionPrepScreen(
            uiState = TutorBridgeUiState(),
            onBack = {}
        )
    }
}
