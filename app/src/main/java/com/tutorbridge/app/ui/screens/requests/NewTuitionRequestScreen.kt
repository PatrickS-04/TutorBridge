package com.tutorbridge.app.ui.screens.requests

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavyTint
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

private val stepTitles = listOf("Subject", "Level", "Time Window", "Venue", "Notes")

@Composable
fun NewTuitionRequestScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSubjectChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
    onTimeWindowChange: (String) -> Unit,
    onVenueChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val step = uiState.requestDraft.step
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
            Text("New Tuition Request", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
            Text("Step $step of $totalSteps — ${stepTitles[step - 1]}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (step) {
            1 -> {
                Text("What subject or course do you need help with?", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = uiState.requestDraft.subject,
                    onValueChange = onSubjectChange,
                    placeholder = { Text("e.g. Algebra II, Organic Chemistry, Jetpack Compose") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            2 -> {
                Text("What level is this course?", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = uiState.requestDraft.level,
                    onValueChange = onLevelChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            3 -> {
                Text("When are you generally available?", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = uiState.requestDraft.timeWindow,
                    onValueChange = onTimeWindowChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            4 -> {
                Text("Preferred campus venue", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = uiState.requestDraft.preferredVenue,
                    onValueChange = onVenueChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            5 -> {
                Text("Notes & Topic Details for Mentors", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = uiState.requestDraft.notes,
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
                "Draft autosaved locally — you can exit anytime and continue later even without Wi-Fi.",
                fontSize = 11.sp,
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
            Button(
                onClick = if (step < totalSteps) onNext else onSubmit,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
            ) {
                Text(if (step < totalSteps) "Next Step" else "Submit Request", fontWeight = FontWeight.Bold)
            }
        }
    }
}
