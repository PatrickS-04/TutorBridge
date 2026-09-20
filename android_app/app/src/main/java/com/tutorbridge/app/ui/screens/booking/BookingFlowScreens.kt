package com.tutorbridge.app.ui.screens.booking

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.TutorUi
import com.tutorbridge.app.data.repository.slotsOn
import com.tutorbridge.app.ui.components.VenueLink
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.CrimsonTint
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.EmeraldTint
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.NavyTint
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

// The next two weeks, starting tomorrow.
private val dayLabel = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

private fun upcomingDates(): List<Pair<String, Pair<String, String>>> =
    (1..14).map { offset ->
        val date = LocalDate.now().plusDays(offset.toLong())
        date.toString() to Pair(date.format(dayLabel).uppercase(Locale.ENGLISH), date.dayOfMonth.toString())
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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Select Availability", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Text(
            selectedDate?.month?.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                ?.plus(" ${selectedDate.year}") ?: "Pick a date",
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
                        .background(if (selected) NavySecondary else MaterialTheme.colorScheme.surface)
                        .clickable { onSelectDate(dateVal) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(dayNum.first, fontSize = 10.sp, color = if (selected) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dayNum.second, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Text("Available Slot (1 hour)", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        if (slots.isEmpty()) {
            Text(
                if (tutor.windows.isEmpty())
                    "${tutor.name.split(" ").first()} has not published any hours yet - pick another tutor."
                else
                    "${tutor.name.split(" ").first()} has no hours set for this day - try another date.",
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
                                containerColor = if (isSelected) NavySecondary else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                slot,
                                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Text("Session Location", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        venues.forEach { venue ->
            val isSelected = uiState.selectedLocation == venue
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectLocation(venue) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) NavySecondary else MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    venue,
                    modifier = Modifier.padding(14.dp),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Google Maps owns the map. Tapping the venue hands it over by intent,
        // which is what the calendar and session screens already do - an
        // embedded MapView bought a thumbnail and cost a crash.
        VenueLink(uiState.selectedLocation)

        Button(
            onClick = onNext,
            enabled = uiState.selectedSlot.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
        ) {
            Text("Continue to Review", fontWeight = FontWeight.Bold)
        }
    }
}

// ==================== STEP 2: REVIEW BOOKING ====================

@Composable
fun ReviewBookingScreen(
    uiState: TutorBridgeUiState,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val tutor = uiState.selectedTutor ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Review Booking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    Text("${tutor.subject} (${tutor.courseCode})", color = CrimsonPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewRow("Date", uiState.selectedBookingDate)
                ReviewRow("Time", "${uiState.selectedSlot} (1 hour)")
                ReviewRow("Location", uiState.selectedLocation)
                ReviewRow("Calendar Sync", "✓ Auto-adds to Calendar tab")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NavyTint)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Place, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(16.dp))
            Text("You will meet ${tutor.name.split(" ").last()} at the confirmed room location above once verified.", fontSize = 11.5.sp, color = NavySecondary)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
        ) {
            Text("Confirm Request", fontWeight = FontWeight.Bold)
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
    onViewCalendar: () -> Unit
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
            modifier = Modifier.size(80.dp).clip(CircleShape).background(NavyTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Booking Request Sent!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your request was submitted and synched with your calendar. Most tutors reply in less than 2 hours.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedButton(
            onClick = onCheckStatus,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) { Text("Check for a reply", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(14.dp))
        TextButton(onClick = onViewCalendar) {
            Text("View in Calendar Tab", color = CrimsonPrimary, fontWeight = FontWeight.Bold)
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
            Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = EmeraldSuccess, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Session Confirmed!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your tutor accepted! Get ready for your session at the location below.",
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
                ReviewRow2("Tutor", tutor?.name ?: "")
                ReviewRow2("Date & Time", "${uiState.selectedBookingDate}, ${uiState.selectedSlot}")
                ReviewRow2("Location", uiState.selectedLocation)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onGoToCalendar,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
        ) {
            Text("View on Calendar", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onBackToDashboard) {
            Text("Back to Dashboard", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Booking Declined", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tutor's note:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "\"$declineNote\"",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = NavySecondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onPickAnotherSlot,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
        ) {
            Text("Pick Another Slot", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onFindAnotherTutor,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("Find Another Tutor", fontWeight = FontWeight.Bold)
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
