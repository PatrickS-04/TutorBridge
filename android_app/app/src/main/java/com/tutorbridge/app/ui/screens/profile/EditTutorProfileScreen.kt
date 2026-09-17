package com.tutorbridge.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun EditTutorProfileScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onLanguagesChange: (String) -> Unit = {},
    onRoomChange: (String) -> Unit = {},
    onSave: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Edit Tutor Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Profile Completeness", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${uiState.tutorProfileCompleteness}%", color = CrimsonPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = uiState.tutorProfileCompleteness / 100f,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = NavySecondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                if (uiState.tutorProfileCompleteness < 100) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Add languages spoken to reach 100%", fontSize = 11.5.sp, color = CrimsonPrimary)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Languages Spoken", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = uiState.tutorLanguagesSpoken,
                onValueChange = onLanguagesChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Designated Campus Consultation Room", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = uiState.tutorConsultationRoom,
                onValueChange = onRoomChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
        ) {
            Text("Save Profile", fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditTutorProfileScreenPreview() {
    TutorBridgeTheme {
        EditTutorProfileScreen(
            uiState = TutorBridgeUiState(),
            onBack = {}
        )
    }
}
