package com.tutorbridge.app.ui.screens.home

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun StudentHomeScreen(
    uiState: TutorBridgeUiState,
    onNavigate: (String) -> Unit
) {
    val firstName = uiState.userName.split(" ").firstOrNull()?.uppercase() ?: "STUDENT"

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
                    Text("HELLO, $firstName", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Let's learn today", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val unread = uiState.threads.sumOf { it.unread }
                    IconButton(onClick = { onNavigate("messages") }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread", fontSize = 12.sp) } }) {
                            Icon(Icons.Default.Chat, contentDescription = "Messages")
                        }
                    }
                    IconButton(onClick = { onNavigate("notifications") }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                }
            }
        }

        // Next session card
        item {
            val nextEvent = uiState.calendarEvents.firstOrNull()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Session prep is now the tutor's private notes page, so
                    // the student's card opens the schedule and venue instead.
                    .clickable { onNavigate("calendar") },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavySecondary)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = CrimsonPrimary) {
                            Text(
                                nextEvent?.let { "Next Session • ${it.startTime}" } ?: "No sessions booked",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text("Schedule & Room →", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                nextEvent?.participantName.orEmpty().split(" ").mapNotNull { part ->
                                    part.firstOrNull()?.uppercase()
                                }.take(2).joinToString(""),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Column {
                            Text(
                                nextEvent?.participantName ?: "Book your first session",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                nextEvent?.subject.orEmpty(),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Text(
                                nextEvent?.location.orEmpty(),
                                color = Color.White.copy(alpha = 0.7f),
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
                QuickAction(Icons.Default.Search, "Find Tutor", Modifier.weight(1f)) { onNavigate("find_tutors") }
                QuickAction(Icons.Default.CalendarMonth, "Calendar", Modifier.weight(1f)) { onNavigate("calendar") }
                QuickAction(Icons.Default.List, "My Requests", Modifier.weight(1f)) { onNavigate("my_requests") }
            }
        }

        // Upcoming on Your Calendar
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Upcoming on Your Calendar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Full Calendar →", color = CrimsonPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        items(uiState.calendarEvents.take(2)) { event ->
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
                        Text(event.location.split("—").firstOrNull()?.trim() ?: "Campus", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text(if (personalised) "Top Tutors For You" else "Tutors on Campus", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "See All",
                    color = CrimsonPrimary,
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
                                    Text("${tutor.matchPercent}% match", color = CrimsonPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text("${tutor.subject} (${tutor.courseCode})", fontSize = 12.sp, color = CrimsonPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                if (tutor.activeNow) "Active now" else if (tutor.activeToday) "Active today" else null,
                                if (tutor.reviewsCount > 0) "★ ${tutor.rating}" else null,
                                if (tutor.verified) "✓ Verified" else null
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
            Icon(icon, contentDescription = label, tint = NavySecondary, modifier = Modifier.size(20.dp))
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
