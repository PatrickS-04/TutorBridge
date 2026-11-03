package com.tutorbridge.app.ui.screens.home

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun StudentHomeScreen(
    uiState: TutorBridgeUiState,
    onNavigate: (String) -> Unit
) {
    val firstName = uiState.userName.split(" ").firstOrNull()?.uppercase() ?: stringResource(R.string.history_default_name)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.student_home_hello, firstName), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.student_home_let_s_learn_today), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val unread = uiState.threads.sumOf { it.unread }
                    IconButton(onClick = { onNavigate("messages") }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread", fontSize = 12.sp) } }) {
                            Icon(Icons.Default.Chat, contentDescription = stringResource(R.string.common_messages))
                        }
                    }
                    IconButton(onClick = { onNavigate("notifications") }) {
                        Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.common_notifications))
                    }
                }
            }
        }

        // Next session card
        item {
            val nextEvent = uiState.upcomingEvents.firstOrNull()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Session prep is now the tutor's private notes page, so
                    // the student's card opens the schedule and venue instead.
                    .clickable { onNavigate("calendar") },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(
                                nextEvent?.let { stringResource(R.string.student_home_next_session, it.startTime) } ?: stringResource(R.string.student_home_no_sessions_booked),
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text(stringResource(R.string.student_home_schedule_room), color = MaterialTheme.colorScheme.onSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                nextEvent?.participantName.orEmpty().split(" ").mapNotNull { part ->
                                    part.firstOrNull()?.uppercase()
                                }.take(2).joinToString(""),
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Column {
                            Text(
                                nextEvent?.participantName ?: stringResource(R.string.student_home_book_your_first_session),
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                nextEvent?.subject.orEmpty(),
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Text(
                                nextEvent?.location.orEmpty(),
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Quick actions row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Default.Search, stringResource(R.string.student_home_find_tutor), Modifier.weight(1f)) { onNavigate("find_tutors") }
                QuickAction(Icons.Default.CalendarMonth, stringResource(R.string.student_home_calendar), Modifier.weight(1f)) { onNavigate("calendar") }
                QuickAction(Icons.Default.List, stringResource(R.string.student_home_my_requests), Modifier.weight(1f)) { onNavigate("my_requests") }
                QuickAction(Icons.Default.History, stringResource(R.string.student_home_history), Modifier.weight(1f)) { onNavigate("learning_history") }
            }
        }

        // Upcoming on Your Calendar
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.student_home_upcoming_on_your_calendar), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(stringResource(R.string.student_home_full_calendar), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        items(uiState.upcomingEvents.take(2)) { event ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate("calendar") },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(event.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text("${event.date} • ${event.startTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(event.location.split("—").firstOrNull()?.trim() ?: stringResource(R.string.student_home_campus), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // "For You" is only claimed when the ranking is real: the server scores a
        // tutor against the student's own requests, and a student with none (or
        // no tutor that fits) gets a plain list instead of a made-up match.
        val personalised = uiState.tutors.any { it.matchPercent > 0 }
        val shownTutors = if (personalised) uiState.tutors.filter { it.matchPercent > 0 } else uiState.tutors

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (personalised) stringResource(R.string.student_home_top_tutors_for_you) else stringResource(R.string.student_home_tutors_on_campus), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    stringResource(R.string.student_home_see_all),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigate("find_tutors") }
                )
            }
        }

        items(shownTutors.take(3)) { tutor ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate("find_tutors") },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text(tutor.initials, fontWeight = FontWeight.Bold) }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tutor.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (tutor.matchPercent > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                    Text(stringResource(R.string.common_percent_match, tutor.matchPercent), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text("${tutor.subject} (${tutor.courseCode})", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                if (tutor.activeNow) stringResource(R.string.common_active_now) else if (tutor.activeToday) stringResource(R.string.common_active_today) else null,
                                if (tutor.reviewsCount > 0) "★ ${tutor.rating}" else null,
                                if (tutor.verified) stringResource(R.string.student_home_verified) else null
                            ).joinToString(" • "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StudentHomeScreenPreview() {
    TutorBridgeTheme {
        StudentHomeScreen(
            uiState = TutorBridgeUiState(),
            onNavigate = {}
        )
    }
}
