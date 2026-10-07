package com.tutorbridge.app.ui.screens.find

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun TutorProfileScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onBookSession: () -> Unit
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
            Text("Tutor Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(tutor.initials, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                Spacer(modifier = Modifier.height(10.dp))
                Text(tutor.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_tutorbridge_logo),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        listOf(tutor.department, tutor.staffOrTutorId).filter { it.isNotBlank() }.joinToString(" • "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${tutor.subject} (${tutor.courseCode})",
                    color = CrimsonPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        if (tutor.reviewsCount > 0) "★ ${tutor.rating} Rating" else null,
                        if (tutor.activeNow) "Active now" else if (tutor.activeToday) "Active today" else "Not available today"
                    ).joinToString("  •  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About ${tutor.name.split(" ").last()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(tutor.bio, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileInfoRow(Icons.Default.School, "Department", tutor.department)
                ProfileInfoRow(Icons.Default.Language, "Languages", tutor.languages.joinToString(", "))
                ProfileInfoRow(Icons.Default.History, "Total Consultations", "${tutor.totalConsultations}+ sessions")
                ProfileInfoRow(Icons.Default.Place, "Consultation Venue", tutor.consultationVenue)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBookSession,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
        ) {
            Text("Select & Choose Time Slot", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = label, tint = NavySecondary, modifier = Modifier.size(18.dp))
        Column {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TutorProfileScreenPreview() {
    TutorBridgeTheme {
        TutorProfileScreen(
            uiState = TutorBridgeUiState(),
            onBack = {},
            onBookSession = {}
        )
    }
}
