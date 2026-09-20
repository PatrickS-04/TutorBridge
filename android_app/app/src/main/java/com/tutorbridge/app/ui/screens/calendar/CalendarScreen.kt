package com.tutorbridge.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.tutorbridge.app.data.model.CalendarEventUi
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.ui.components.VenueLink
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    uiState: TutorBridgeUiState,
    onSelectDate: (LocalDate) -> Unit,
    onFilterChange: (String) -> Unit,
    onNavigateToPrep: () -> Unit
) {
    val isTutor = uiState.role == UserRole.TUTOR
    val yearMonth = YearMonth.from(uiState.selectedCalendarDate)
    val eventsOnDate = uiState.calendarEvents.filter { it.date == uiState.selectedCalendarDate.toString() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isTutor) "TUTOR SCHEDULE" else "STUDENT SCHEDULE",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Calendar & Events", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                // Sessions are created by the student who needs them - a tutor
                // only ever accepts or declines one, so they get no Add button.
                if (!isTutor) {
                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavySecondary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Event", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Month header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NavySecondary)
                        Column {
                            Text(
                                "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${yearMonth.year}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text("${uiState.calendarEvents.count { YearMonth.from(LocalDate.parse(it.date)) == yearMonth }} scheduled events", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row {
                        IconButton(onClick = { onSelectDate(uiState.selectedCalendarDate.minusMonths(1)) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev")
                        }
                        IconButton(onClick = { onSelectDate(uiState.selectedCalendarDate.plusMonths(1)) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                        }
                    }
                }
            }
        }

        // Month grid
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEach { d ->
                            Text(
                                d,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val firstOfMonth = yearMonth.atDay(1)
                    val leadingBlanks = firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
                    val daysInMonth = yearMonth.lengthOfMonth()
                    val totalCells = leadingBlanks + daysInMonth
                    val rows = (totalCells + 6) / 7

                    for (row in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0..6) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - leadingBlanks + 1
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dayNum in 1..daysInMonth) {
                                        val date = yearMonth.atDay(dayNum)
                                        val isSelected = date == uiState.selectedCalendarDate
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(if (isSelected) NavySecondary else Color.Transparent)
                                                .clickable { onSelectDate(date) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "$dayNum",
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All Events", "1-on-1 Lessons", "Office Hours", "Exam Prep").forEach { filter ->
                    FilterChip(
                        selected = uiState.calendarFilterType.equals(filter, ignoreCase = true) ||
                            (filter == "All Events" && uiState.calendarFilterType == "all"),
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter, fontSize = 11.5.sp) }
                    )
                }
            }
        }

        // Agenda
        item {
            Text(
                "Agenda • ${uiState.selectedCalendarDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}, ${uiState.selectedCalendarDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${uiState.selectedCalendarDate.dayOfMonth}   ${eventsOnDate.size} items",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        if (eventsOnDate.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, tint = CrimsonPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No Sessions on This Date", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Schedule a personal study block or book a tutor from the directory.",
                        fontSize = 11.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            items(eventsOnDate) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToPrep() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${event.startTime} - ${event.endTime}", fontWeight = FontWeight.Bold, color = CrimsonPrimary, fontSize = 13.sp)
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                Text(event.type.name, color = CrimsonPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(event.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        VenueLink(event.location, fontSize = 12.5.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("With ${event.participantName}", fontSize = 12.sp, color = NavySecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
    TutorBridgeTheme {
        CalendarScreen(
            uiState = TutorBridgeUiState(),
            onSelectDate = {},
            onFilterChange = {},
            onNavigateToPrep = {}
        )
    }
}
