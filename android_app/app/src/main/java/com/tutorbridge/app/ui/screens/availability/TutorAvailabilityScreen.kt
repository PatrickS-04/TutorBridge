package com.tutorbridge.app.ui.screens.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun TutorAvailabilityScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onToggleDay: (String) -> Unit = {},
    onEditHours: (day: String, startTime: String, endTime: String) -> Unit = { _, _, _ -> }
) {
    // Keyed by DayOfWeek name so Tuesday and Thursday stay distinct.
    val days = java.time.DayOfWeek.entries

    // Which row's hours are open for editing, if any - local to this screen,
    // closed again once the save fires.
    var editingDay by remember { mutableStateOf<String?>(null) }
    var editStart by remember { mutableStateOf("") }
    var editEnd by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // No bottom bar on this page, so this is the way back out.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Availability", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            "Manage recurring weekly hours.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                val isEditing = editingDay == window.dayName
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isEditing) {
                                editingDay = null
                            } else {
                                editingDay = window.dayName
                                val hours = window.timeRange.split("–").map { it.trim() }
                                editStart = hours.getOrElse(0) { "09:00" }
                                editEnd = hours.getOrElse(1) { "17:00" }
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                        if (isEditing) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = editStart,
                                    onValueChange = { editStart = it },
                                    label = { Text("From (HH:mm)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editEnd,
                                    onValueChange = { editEnd = it },
                                    label = { Text("To (HH:mm)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                IconButton(onClick = {
                                    onEditHours(window.dayName, editStart, editEnd)
                                    editingDay = null
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save hours")
                                }
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
fun TutorAvailabilityScreenPreview() {
    TutorBridgeTheme {
        TutorAvailabilityScreen(
            uiState = TutorBridgeUiState(),
            onBack = {}
        )
    }
}
