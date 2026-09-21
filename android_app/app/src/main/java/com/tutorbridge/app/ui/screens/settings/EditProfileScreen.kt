package com.tutorbridge.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun EditProfileScreen(
    uiState: TutorBridgeUiState,
    onLoadProfile: () -> Unit,
    onSave: (email: String, bio: String, hourlyRate: Double?, department: String) -> Unit
) {
    LaunchedEffect(Unit) { onLoadProfile() }

    val profile = uiState.currentProfile
    val isTutor = uiState.role == UserRole.TUTOR

    var email by remember { mutableStateOf(Firebase.auth.currentUser?.email ?: "") }
    var bio by remember(profile) { mutableStateOf(profile?.bio ?: "") }
    var hourlyRate by remember(profile) { mutableStateOf(profile?.hourlyRate?.toString() ?: "") }
    var department by remember(profile) { mutableStateOf(profile?.department ?: "") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Edit Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = bio, onValueChange = { bio = it },
            label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 3
        )

        if (isTutor) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = hourlyRate, onValueChange = { hourlyRate = it },
                label = { Text("Hourly Rate (R)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = department, onValueChange = { department = it },
                label = { Text("Department / Faculty") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }

        uiState.authErrorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        uiState.profileStatusMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSave(email, bio, if (isTutor) hourlyRate.toDoubleOrNull() else null, department) },
            enabled = !uiState.isAuthLoading && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (uiState.isAuthLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}