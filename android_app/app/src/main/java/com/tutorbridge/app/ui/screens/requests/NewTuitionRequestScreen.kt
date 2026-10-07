package com.tutorbridge.app.ui.screens.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
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
import com.tutorbridge.app.ui.theme.NavyTint
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.data.model.NamedIdDto
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val stepTitles = listOf("Subject", "Level", "Time Window", "Venue", "Notes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTuitionRequestScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectSubject: (String, String) -> Unit,
    onLevelChange: (String) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onHoursChange: (String, String) -> Unit,
    onSelectArea: (String, String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val draft = uiState.requestDraft
    val step = draft.step
    val totalSteps = 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = if (step > 1) onPrevious else onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (draft.editingRequestId != null) "Edit Tuition Request" else "New Tuition Request",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Step progress dots
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 1..totalSteps) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) CrimsonPrimary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Step $step of $totalSteps — ${stepTitles[step - 1]}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (step) {
            1 -> {
                Text("What subject or course do you need help with?", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                PickerField(
                    label = draft.subject,
                    placeholder = "Choose a subject",
                    options = uiState.subjectOptions,
                    onSelect = onSelectSubject
                )
            }
            2 -> {
                Text("What level is this course?", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = draft.level,
                    onValueChange = onLevelChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            3 -> {
                Text("When are you generally available?", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = draft.days.contains(day)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selected) CrimsonPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onToggleDay(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.startTime,
                        onValueChange = { onHoursChange(it, draft.endTime) },
                        label = { Text("From (HH:mm)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.endTime,
                        onValueChange = { onHoursChange(draft.startTime, it) },
                        label = { Text("To (HH:mm)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }
            }
            4 -> {
                Text("Preferred campus area", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                PickerField(
                    label = draft.preferredVenue,
                    placeholder = "Choose a campus area",
                    options = uiState.areaOptions,
                    onSelect = onSelectArea
                )
            }
            5 -> {
                Text("Notes & Topic Details for Mentors", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = onNotesChange,
                    placeholder = { Text("Need help with limits and derivatives") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NavyTint)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Bookmark, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(16.dp))
            Text(
                "Your request goes to every verified tutor for this subject at your institution.",
                fontSize = 12.sp,
                color = NavySecondary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 1) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) { Text("Back", fontWeight = FontWeight.Bold) }
            }
            val canContinue = when (step) {
                1 -> draft.subjectId.isNotBlank()
                4 -> draft.areaId.isNotBlank()
                else -> true
            }
            Button(
                onClick = if (step < totalSteps) onNext else onSubmit,
                enabled = canContinue && !uiState.isRefreshing,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
            ) {
                val submitLabel = if (draft.editingRequestId != null) "Save Changes" else "Submit Request"
                Text(if (step < totalSteps) "Next Step" else submitLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A read-only field that opens the reference-data list it is given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    label: String,
    placeholder: String,
    options: List<NamedIdDto>,
    onSelect: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("Nothing to choose from yet") }, onClick = {}, enabled = false)
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option.id, option.name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NewTuitionRequestScreenPreview() {
    TutorBridgeTheme {
        NewTuitionRequestScreen(
            uiState = TutorBridgeUiState(),
            onBack = {},
            onNext = {},
            onPrevious = {},
            onSelectSubject = { _, _ -> },
            onLevelChange = {},
            onToggleDay = {},
            onHoursChange = { _, _ -> },
            onSelectArea = { _, _ -> },
            onNotesChange = {},
            onSubmit = {}
        )
    }
}
