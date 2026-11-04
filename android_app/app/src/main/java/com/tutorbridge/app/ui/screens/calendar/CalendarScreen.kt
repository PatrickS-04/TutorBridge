package com.tutorbridge.app.ui.screens.calendar

import androidx.compose.ui.res.pluralStringResource
import com.tutorbridge.app.R
import androidx.compose.ui.res.stringResource
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
import com.tutorbridge.app.data.model.EventStatus
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.data.model.sessionsFrom
import com.tutorbridge.app.ui.components.PendingBadge
import com.tutorbridge.app.ui.components.SeriesBadge
import com.tutorbridge.app.ui.components.VenueLink
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.ScannedCheckIn
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.platform.LocalContext

@Composable
fun CalendarScreen(
    uiState: TutorBridgeUiState,
    onSelectDate: (LocalDate) -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenSessionNotes: (CalendarEventUi) -> Unit,
    onAddEvent: () -> Unit,
    onReschedule: (CalendarEventUi, LocalDate, LocalTime) -> Unit = { _, _, _ -> },
    onShowCheckInCode: (CalendarEventUi) -> Unit = {},
    onDismissCheckInCode: () -> Unit = {},
    onScannedCheckIn: (CalendarEventUi, String?) -> Unit = { _, _ -> },
    onTypedCheckIn: (CalendarEventUi, String) -> Unit = { _, _ -> },
    onScannerUnavailable: () -> Unit = {},
    onCancelSeries: (CalendarEventUi) -> Unit = {}
) {
    val isTutor = uiState.role == UserRole.TUTOR
    // The session being moved, while its dialog is open.
    var rescheduling by remember { mutableStateOf<CalendarEventUi?>(null) }
    rescheduling?.let { event ->
        RescheduleDialog(
            event = event,
            onDismiss = { rescheduling = null },
            onConfirm = { date, time ->
                rescheduling = null
                onReschedule(event, date, time)
            }
        )
    }
    val context = LocalContext.current
    // The session the student is typing a code for, while that dialog is open
    var enteringCodeFor by remember { mutableStateOf<CalendarEventUi?>(null) }
    enteringCodeFor?.let { event ->
        EnterCheckInCodeDialog(
            onSubmit = { code -> enteringCodeFor = null; onTypedCheckIn(event, code) },
            onDismiss = { enteringCodeFor = null }
        )
    }
    uiState.checkInCode?.let { code ->
        CheckInCodeDialog(
            code = code,
            onNewCode = { uiState.calendarEvents.firstOrNull { it.id == code.bookingId }?.let(onShowCheckInCode) },
            onDismiss = onDismissCheckInCode
        )
    }
    // The series session being cancelled "and following", while its confirmation is open.
    var cancellingSeries by remember { mutableStateOf<CalendarEventUi?>(null) }
    cancellingSeries?.let { event ->
        val sessions = sessionsFrom(event.seriesIndex, event.seriesCount)
        AlertDialog(
            onDismissRequest = { cancellingSeries = null },
            title = { Text(stringResource(R.string.calendar_series_cancel_title)) },
            text = { Text(pluralStringResource(R.plurals.calendar_series_cancel_body, sessions, sessions)) },
            confirmButton = {
                TextButton(onClick = {
                    cancellingSeries = null
                    onCancelSeries(event)
                }) {
                    Text(stringResource(R.string.calendar_series_cancel_confirm), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancellingSeries = null }) {
                    Text(stringResource(R.string.calendar_series_cancel_keep))
                }
            }
        )
    }
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
                        if (isTutor) stringResource(R.string.calendar_tutor_schedule) else stringResource(R.string.calendar_student_schedule),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.calendar_calendar_events), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                // Sessions are created by the student who needs them - a tutor
                // only ever accepts or declines one, so they get no Add button.
                if (!isTutor) {
                    Button(
                        onClick = onAddEvent,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.calendar_add_event), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Column {
                            Text(
                                "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(uiState.calendarEvents.count { YearMonth.from(LocalDate.parse(it.date)) == yearMonth }.let { n -> pluralStringResource(R.plurals.calendar_scheduled_events, n, n) }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row {
                        IconButton(onClick = { onSelectDate(uiState.selectedCalendarDate.minusMonths(1)) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.calendar_prev))
                        }
                        IconButton(onClick = { onSelectDate(uiState.selectedCalendarDate.plusMonths(1)) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.calendar_next))
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
                        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()) }.forEach { d ->
                            Text(
                                d,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 12.sp,
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
                                                .background(if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                                .clickable { onSelectDate(date) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "$dayNum",
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
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
                listOf("All Events" to R.string.calendar_filter_all, "1-on-1 Lessons" to R.string.calendar_filter_lessons, "Office Hours" to R.string.calendar_filter_office_hours, "Exam Prep" to R.string.calendar_filter_exam_prep).forEach { (filter, labelRes) ->
                    FilterChip(
                        selected = uiState.calendarFilterType.equals(filter, ignoreCase = true) ||
                            (filter == "All Events" && uiState.calendarFilterType == "all"),
                        onClick = { onFilterChange(filter) },
                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                    )
                }
            }
        }

        // Agenda
        item {
            Text(
                pluralStringResource(R.plurals.calendar_agenda, eventsOnDate.size, uiState.selectedCalendarDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), uiState.selectedCalendarDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), uiState.selectedCalendarDate.dayOfMonth, eventsOnDate.size),
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
                        Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.calendar_no_sessions_on_this_date), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.calendar_schedule_a_personal_study_block),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            items(eventsOnDate) { event ->
                Card(
                    // The notes behind a session are the tutor's own, so only
                    // they get a card that opens onto them.
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isTutor) Modifier.clickable { onOpenSessionNotes(event) } else Modifier
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${event.startTime} - ${event.endTime}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                Text(event.type.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(event.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (event.isPending) {
                            Spacer(modifier = Modifier.height(4.dp))
                            PendingBadge()
                        }
                        if (event.isSeries) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SeriesBadge(event.seriesIndex, event.seriesCount)
                        }
                        VenueLink(event.location, fontSize = 12.5.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.calendar_with_person, event.participantName), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        CheckInRow(
                            event = event,
                            isTutor = isTutor,
                            // The server checks the code, so this needs a connection
                            enabled = uiState.isOnline && !uiState.isCheckingIn,
                            onShowCode = { onShowCheckInCode(event) },
                            onScan = { scanCheckInQr(context, { raw -> onScannedCheckIn(event, raw) }, onScannerUnavailable) },
                            onEnterCode = { enteringCodeFor = event }
                        )

                        if (isTutor) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    if (event.checklist.isEmpty()) {
                                        stringResource(R.string.calendar_add_session_notes)
                                    } else {
                                        stringResource(R.string.calendar_notes_progress, event.checklist.count { it.completed }, event.checklist.size)
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // A session that is still ahead can be moved instead of
                        // cancelled and booked again.
                        if (event.isUpcoming()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { rescheduling = event },
                                // The server checks the new time against the tutor's diary, so this needs a connection.
                                enabled = uiState.isOnline,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.calendar_reschedule), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            // A weekly series can be dropped from this session onward in one go.
                            if (event.isSeries) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { cancellingSeries = event },
                                    enabled = uiState.isOnline,
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.calendar_series_cancel),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
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

/** On and still ahead: a finished or cancelled session cannot be moved, and neither can one that has begun. */
private fun CalendarEventUi.isUpcoming(): Boolean {
    if (status != EventStatus.CONFIRMED) return false
    val start = runCatching { Instant.parse(startIso) }.getOrNull() ?: return false
    return start.isAfter(Instant.now())
}

private val rescheduleHours = (7..20).map { LocalTime.of(it, 0) }

/** The start times a session can be moved to on [date]: on today, only those still ahead of [now]. */
internal fun rescheduleHoursOn(date: LocalDate, now: LocalDateTime): List<LocalTime> =
    rescheduleHours.filter { date.atTime(it).isAfter(now) }

private val hourLabel get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dayLabel get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/**
 * Pick a new day and start time for [event]. The length of the session is kept;
 * the server refuses a time the tutor already has a confirmed session in.
 */
@Composable
private fun RescheduleDialog(
    event: CalendarEventUi,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime) -> Unit
) {
    // Fixed when the dialog opens, so the hours can't run out under it; the server refuses a time that has since passed.
    val now = remember { LocalDateTime.now() }
    // Today is only offered while it has an hour left.
    val days = remember { (0..21).map { now.toLocalDate().plusDays(it.toLong()) }.filter { rescheduleHoursOn(it, now).isNotEmpty() } }
    var date by remember { mutableStateOf(days.first()) }
    var preferredTime by remember { mutableStateOf(LocalTime.of(14, 0)) }
    val hours = rescheduleHoursOn(date, now)
    // A time picked on another day may already have passed today.
    val time = if (preferredTime in hours) preferredTime else hours.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_reschedule_session)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.calendar_reschedule_current, event.subject, event.participantName, event.date, event.startTime),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.calendar_day), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    days.forEach { day ->
                        FilterChip(
                            selected = day == date,
                            onClick = { date = day },
                            label = { Text(day.format(dayLabel), fontSize = 12.sp) }
                        )
                    }
                }
                Text(stringResource(R.string.calendar_start_time), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hours.forEach { hour ->
                        FilterChip(
                            selected = hour == time,
                            onClick = { preferredTime = hour },
                            label = { Text(hour.format(hourLabel), fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date, time) }) {
                Text(stringResource(R.string.calendar_move_session), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_keep_current_time)) } }
    )
}

@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
    TutorBridgeTheme {
        CalendarScreen(
            uiState = TutorBridgeUiState(),
            onSelectDate = {},
            onFilterChange = {},
            onOpenSessionNotes = {},
            onAddEvent = {}
        )
    }
}