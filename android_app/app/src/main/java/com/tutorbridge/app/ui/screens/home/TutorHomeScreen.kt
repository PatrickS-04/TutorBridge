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
import androidx.compose.foundation.lazy.items
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun TutorHomeScreen(
    uiState: TutorBridgeUiState,
    onNavigate: (String) -> Unit,
    onAcceptBooking: (String) -> Unit = {},
    onDeclineBooking: (String) -> Unit = {}
) {
    val institution = uiState.institutions.find { it.id == uiState.selectedInstitutionId }
        ?: uiState.institutions.firstOrNull()
    val activePeriod = institution?.claimPeriods?.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FACULTY TUTOR PORTAL", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(uiState.userName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { onNavigate("notifications") }) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldSuccess))
                val staffId = uiState.userStaffId.ifBlank { institution?.staffId.orEmpty() }
                Text(
                    if (staffId.isBlank()) "Dept. Appointed Tutor" else "Staff #$staffId • Dept. Appointed Tutor",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text("Active", color = EmeraldSuccess, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Timesheets & Claims Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("claims_export") },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavySecondary)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TIMESHEETS & CLAIMS", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${activePeriod?.label ?: "This month"} Pay Period", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Badge(containerColor = CrimsonPrimary) {
                            Text("CLAIMABLE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Logged Hours", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("${activePeriod?.totalHours ?: 0.0} hrs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column {
                            Text("Claim Amount", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("R${(activePeriod?.claimValue ?: 0.0).toInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Export & Submit Claim to Faculty →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }

        // Schedule Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("calendar") },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tutor Schedule & Calendar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("View bookings & manage availability →", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("Open", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Bookings waiting on this tutor
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "New Student Bookings (${uiState.pendingBookings.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        if (uiState.pendingBookings.isEmpty()) {
            item {
                Text(
                    "No bookings are waiting for a decision.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(uiState.pendingBookings) { booking ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text(booking.initials, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        Column {
                            Text(booking.studentName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text(
                                listOf(booking.subjectName, booking.whenLabel, booking.location)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onDeclineBooking(booking.id) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) { Text("Decline", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
                        Button(
                            onClick = { onAcceptBooking(booking.id) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
                        ) { Text("Accept & Sync", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White) }
                    }
                }
            }
        }

        // Upcoming sessions, straight off the calendar
        item {
            Text("Upcoming Sessions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(uiState.calendarEvents.take(3)) { event ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate("calendar") },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(event.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Text(
                        "${event.date} • ${event.startTime}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TutorHomeScreenPreview() {
    TutorBridgeTheme {
        TutorHomeScreen(
            uiState = TutorBridgeUiState(),
            onNavigate = {}
        )
    }
}
