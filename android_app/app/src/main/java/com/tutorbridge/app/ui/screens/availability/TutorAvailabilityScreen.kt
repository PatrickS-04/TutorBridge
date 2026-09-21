package com.tutorbridge.app.ui.screens.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.EmeraldTint
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun TutorAvailabilityScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onTabChange: (String) -> Unit = {},
    onToggleDay: (String) -> Unit = {}
) {
    // Keyed by DayOfWeek name so Tuesday and Thursday stay distinct.
    val days = java.time.DayOfWeek.entries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Availability", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Manage recurring weekly hours & one-off teaching slots.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            SegTabAvail("Weekly Schedule", uiState.availabilityTab == "weekly", Modifier.weight(1f)) { onTabChange("weekly") }
            SegTabAvail("One-off Slots", uiState.availabilityTab == "oneoff", Modifier.weight(1f)) { onTabChange("oneoff") }
        }

        if (uiState.availabilityTab == "weekly") {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Recurring Teaching Days", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Tap days when you are available for student bookings.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                days.forEach { dayOfWeek ->
                                    val d = dayOfWeek.name
                                    val active = uiState.activeDays.contains(d)
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (active) CrimsonPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onToggleDay(d) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            d.take(1),
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Configured Day Windows", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                items(uiState.weeklyAvailability) { window ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(window.day, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(window.timeRange, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Badge(containerColor = EmeraldTint) {
                                Text(if (window.active) "Active" else "Inactive", color = EmeraldSuccess, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(uiState.oneOffSlots) { slot ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(slot.date, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavySecondary)
                            Text(slot.timeRange, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (slot.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(slot.note, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegTabAvail(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) NavySecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TutorAvailabilityScreenPreview() {
    TutorBridgeTheme {
        TutorAvailabilityScreen(
            uiState = TutorBridgeUiState(),
            onBack = {}
        )
    }
}
