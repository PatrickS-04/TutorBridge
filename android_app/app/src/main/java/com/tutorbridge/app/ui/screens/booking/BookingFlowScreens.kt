package com.tutorbridge.app.ui.screens.booking

import com.tutorbridge.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tutorbridge.app.ui.components.NeedsConnectionHint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.SERIES_MIN
import com.tutorbridge.app.data.model.TutorUi
import com.tutorbridge.app.data.model.availableDays
import com.tutorbridge.app.data.model.maxWeeksFor
import com.tutorbridge.app.data.model.sessionDates
import com.tutorbridge.app.data.repository.slotsOn
import com.tutorbridge.app.ui.components.VenueLink
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.tutorbridge.app.ui.theme.CrimsonTint
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.EmeraldTint
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

// The next two weeks, starting tomorrow.
private val dayLabel get() = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private fun upcomingDates(): List<Pair<String, Pair<String, String>>> =
    (1..14).map { offset ->
        val date = LocalDate.now().plusDays(offset.toLong())
        date.toString() to Pair(date.format(dayLabel).uppercase(Locale.getDefault()), date.dayOfMonth.toString())
    }

// ==================== STEP 1: SELECT AVAILABILITY ====================

@Composable
fun BookingFlowScreen(
    uiState: TutorBridgeUiState,
    onSelectSlot: (String) -> Unit,
    onSelectLocation: (String) -> Unit,
    onSelectDate: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val tutor = uiState.selectedTutor ?: return
    val dates = remember { upcomingDates() }
    val selectedDate = runCatching { LocalDate.parse(uiState.selectedBookingDate) }.getOrNull()
    val slots = selectedDate?.let { tutor.slotsOn(it) }.orEmpty()
    // Campus areas come from the institution's reference data; the tutor's own
    // consultation venue is offered first when they have one.
    val venues = (listOf(tutor.consultationVenue) + uiState.areaOptions.map { it.name })
        .filter { it.isNotBlank() }
        .distinct()
    // ...and whatever the student typed in below instead - a coffee shop, a
    // public library - which is simply the location no listed venue matches.
    val customVenue = uiState.selectedLocation.takeUnless { it in venues }.orEmpty()

    // A plain scrolling Column, not a LazyColumn: this screen is about 25 rows,
    // so laziness buys nothing, and a lazy layout force-measures its children
    // through a subcomposition - which is what the MapView below cannot survive.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.booking_select_availability), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Text(
            selectedDate?.month?.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                ?.plus(" ${selectedDate.year}") ?: stringResource(R.string.booking_pick_a_date),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dates.forEach { (dateVal, dayNum) ->
                val selected = uiState.selectedBookingDate == dateVal
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                        .clickable { onSelectDate(dateVal) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(dayNum.first, fontSize = 12.sp, color = if (selected) MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dayNum.second, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Text(stringResource(R.string.booking_available_slot_1_hour), fontWeight = FontWeight.Bold, fontSize = 14.sp)

        if (slots.isEmpty()) {
            Text(
                if (tutor.windows.isEmpty())
                    stringResource(R.string.booking_no_hours_published, tutor.name.split(" ").first())
                else
                    stringResource(R.string.booking_no_hours_day, tutor.name.split(" ").first()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { slot ->
                        val isSelected = uiState.selectedSlot == slot
                        Card(
                            modifier = Modifier.weight(1f).clickable { onSelectSlot(slot) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                slot,
                                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Text(stringResource(R.string.booking_session_location), fontWeight = FontWeight.Bold, fontSize = 14.sp)

        venues.forEach { venue ->
            val isSelected = uiState.selectedLocation == venue
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectLocation(venue) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    venue,
                    modifier = Modifier.padding(14.dp),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        OutlinedTextField(
            value = customVenue,
            // Capped where the server caps it, so a long paste fails here and not on submit.
            onValueChange = { if (it.length <= 120) onSelectLocation(it) },
            label = { Text(stringResource(R.string.booking_somewhere_else)) },
            placeholder = { Text(stringResource(R.string.booking_e_g_the_daily_grind)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Google Maps owns the map. Tapping the venue hands it over by intent,
        // which is what the calendar and session screens already do - an
        // embedded MapView bought a thumbnail and cost a crash.
        VenueLink(uiState.selectedLocation)

        Button(
            onClick = onNext,
            enabled = uiState.selectedSlot.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.booking_continue_to_review), fontWeight = FontWeight.Bold)
        }
    }
}

// ==================== STEP 2: REVIEW BOOKING ====================

@Composable
fun ReviewBookingScreen(
    uiState: TutorBridgeUiState,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onRepeatChange: (Boolean) -> Unit = {},
    onRepeatCountChange: (Int) -> Unit = {},
    onToggleDay: (DayOfWeek) -> Unit = {}
) {
    val tutor = uiState.selectedTutor ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.booking_review_booking), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(tutor.initials, fontWeight = FontWeight.Bold) }
                Column {
                    Text(tutor.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${tutor.subject} (${tutor.courseCode})", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewRow(stringResource(R.string.booking_date), uiState.selectedBookingDate)
                ReviewRow(stringResource(R.string.booking_time), stringResource(R.string.booking_time_one_hour, uiState.selectedSlot))
                ReviewRow(stringResource(R.string.booking_location), uiState.selectedLocation)
                ReviewRow(stringResource(R.string.booking_calendar_sync), stringResource(R.string.booking_auto_adds_to_calendar_tab))
            }
        }

        // A session the student already agreed with a tutor is one session; only a direct booking can repeat.
        if (!uiState.bookingFromApplication) {
            RepeatCard(
                uiState = uiState,
                onToggleDay = onToggleDay,
                onRepeatChange = onRepeatChange,
                onCountChange = onRepeatCountChange
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.booking_you_will_meet, tutor.name.split(" ").last()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // How many sessions this will ask for: the days booked, for one week or for each week it repeats.
        val sessions = uiState.bookedDays.size * (if (uiState.repeatWeekly) uiState.repeatCount else 1)
        val series = !uiState.bookingFromApplication && sessions >= 2
        Button(
            onClick = onConfirm,
            enabled = uiState.isOnline,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                if (series) stringResource(R.string.booking_confirm_series, sessions)
                else stringResource(R.string.booking_confirm_request),
                fontWeight = FontWeight.Bold
            )
        }
        NeedsConnectionHint(uiState.isOnline)
    }
}

/**
 * Which days to book, whether to repeat them weekly, and the dates that result.
 *
 * The date picked above is the first session and its weekday is always booked. Every other day, and every following
 * week, is counted from it in rolling blocks of seven days (see [sessionDates]), so the dates listed here are the
 * dates the server books.
 */
@Composable
private fun RepeatCard(
    uiState: TutorBridgeUiState,
    onToggleDay: (DayOfWeek) -> Unit,
    onRepeatChange: (Boolean) -> Unit,
    onCountChange: (Int) -> Unit
) {
    val firstDate = runCatching { LocalDate.parse(uiState.selectedBookingDate) }.getOrNull()
    val weeks = if (uiState.repeatWeekly) uiState.repeatCount else 1
    val dates = firstDate?.let { sessionDates(it, uiState.repeatDays, weeks) }.orEmpty()
    val maxWeeks = maxWeeksFor(uiState.bookedDays.size)
    // Only days the tutor works at this time can be added; the chosen date's own weekday is always on.
    val open = availableDays(uiState.selectedTutor?.windows.orEmpty(), uiState.selectedSlot)
    val dayName = { day: DayOfWeek -> day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }
    val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(stringResource(R.string.booking_days_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    stringResource(R.string.booking_days_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeek.values().forEach { day ->
                    FilterChip(
                        selected = day in uiState.bookedDays,
                        onClick = { onToggleDay(day) },
                        enabled = day == uiState.firstDay || day in open,
                        label = { Text(dayName(day), fontSize = 12.sp) }
                    )
                }
            }

            if (DayOfWeek.values().any { it != uiState.firstDay && it !in open }) {
                Text(
                    stringResource(R.string.booking_days_unavailable),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.booking_repeat_weekly), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.booking_repeat_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = uiState.repeatWeekly, onCheckedChange = onRepeatChange)
            }

            if (uiState.repeatWeekly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.booking_repeat_weeks), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onCountChange(uiState.repeatCount - 1) },
                            enabled = uiState.repeatCount > SERIES_MIN
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.booking_repeat_fewer))
                        }
                        Text("${uiState.repeatCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(
                            onClick = { onCountChange(uiState.repeatCount + 1) },
                            enabled = uiState.repeatCount < maxWeeks
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.booking_repeat_more))
                        }
                    }
                }
            }

            // Two or more dates is a series; one is the ordinary booking and needs no list.
            if (dates.size >= 2) {
                Text(
                    stringResource(
                        R.string.booking_pattern_summary,
                        uiState.bookedDays.joinToString(", ") { dayName(it) },
                        uiState.selectedSlot
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    pluralStringResource(R.plurals.booking_pattern_total, dates.size, dates.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.booking_repeat_dates), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                dates.forEachIndexed { index, date ->
                    Text("${index + 1}.  ${date.format(dateFormat)}", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ==================== STEP 3: BOOKING REQUEST SENT ====================

@Composable
fun BookingRequestSentScreen(
    onCheckStatus: () -> Unit,
    onViewCalendar: () -> Unit,
    /** Sessions in the weekly series just requested, or 0 for a single session. */
    seriesCount: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.booking_booking_request_sent), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        if (seriesCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.booking_series_requested, seriesCount, seriesCount),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(
                if (seriesCount > 0) R.string.booking_series_submitted else R.string.booking_your_request_was_submitted_and
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedButton(
            onClick = onCheckStatus,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(24.dp)
        ) { Text(stringResource(R.string.booking_check_for_a_reply), fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(14.dp))
        TextButton(onClick = onViewCalendar) {
            Text(stringResource(R.string.booking_view_in_calendar_tab), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

// ==================== STEP 4a: SESSION CONFIRMED ====================

@Composable
fun BookingConfirmationScreen(
    uiState: TutorBridgeUiState,
    onGoToCalendar: () -> Unit,
    onBackToDashboard: () -> Unit = {}
) {
    val tutor = uiState.selectedTutor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(EmeraldTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.booking_success), tint = EmeraldSuccess, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.booking_session_confirmed), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.booking_your_tutor_accepted_get_ready),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewRow2(stringResource(R.string.booking_tutor), tutor?.name ?: "")
                ReviewRow2(stringResource(R.string.booking_date_time), "${uiState.selectedBookingDate}, ${uiState.selectedSlot}")
                ReviewRow2(stringResource(R.string.booking_location), uiState.selectedLocation)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onGoToCalendar,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(stringResource(R.string.booking_view_on_calendar), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onBackToDashboard) {
            Text(stringResource(R.string.common_back_to_dashboard), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewRow2(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ==================== STEP 4b: BOOKING DECLINED ====================

@Composable
fun BookingDeclinedScreen(
    declineNote: String,
    onPickAnotherSlot: () -> Unit,
    onFindAnotherTutor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(CrimsonTint),
            contentAlignment = Alignment.Center
        ) {
            Text("🙁", fontSize = 32.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.booking_booking_declined), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.booking_tutor_s_note), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "\"$declineNote\"",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onPickAnotherSlot,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(stringResource(R.string.booking_pick_another_slot), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onFindAnotherTutor,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text(stringResource(R.string.booking_find_another_tutor), fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BookingFlowScreenPreview() {
    TutorBridgeTheme {
        BookingFlowScreen(
            uiState = TutorBridgeUiState(),
            onSelectSlot = {},
            onSelectLocation = {},
            onSelectDate = {},
            onNext = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ReviewBookingScreenPreview() {
    TutorBridgeTheme {
        ReviewBookingScreen(
            uiState = TutorBridgeUiState(),
            onConfirm = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BookingRequestSentScreenPreview() {
    TutorBridgeTheme {
        BookingRequestSentScreen(
            onCheckStatus = {},
            onViewCalendar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BookingConfirmationScreenPreview() {
    TutorBridgeTheme {
        BookingConfirmationScreen(
            uiState = TutorBridgeUiState(),
            onGoToCalendar = {},
            onBackToDashboard = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BookingDeclinedScreenPreview() {
    TutorBridgeTheme {
        BookingDeclinedScreen(
            declineNote = "Sorry, I have an unexpected faculty meeting at this hour.",
            onPickAnotherSlot = {},
            onFindAnotherTutor = {}
        )
    }
}
