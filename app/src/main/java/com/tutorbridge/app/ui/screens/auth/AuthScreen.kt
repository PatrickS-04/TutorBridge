package com.tutorbridge.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.AuthMode
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.NavyTint
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun AuthScreen(
    uiState: TutorBridgeUiState,
    onRoleSelect: (UserRole) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onToggleAuthMode: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Role is inferred from the email pattern in the real prototype;
    // default to student unless the user has already picked tutor elsewhere in the app.
    val role = uiState.role ?: UserRole.STUDENT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Logo
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.School, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(38.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TutorBridge", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = NavySecondary)
                Text("Higher Education Tutoring Network", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Text("SA Campuses", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Institutional Partner
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("INSTITUTIONAL PARTNER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = uiState.institutionName,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Role-sorted access info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavyTint)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(15.dp))
                    Text("Role-Sorted Campus Access", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = NavySecondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Students: Sign in with your campus Google or student email to book tutors & join sessions.",
                    fontSize = 11.5.sp,
                    color = NavySecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tutors: Log in with your admin-provisioned faculty credentials to access timesheets and export claims.",
                    fontSize = 11.5.sp,
                    color = NavySecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Campus Email or Staff ID", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("e.g. ndxlia003@myuct.ac.za or tutor.clara@uct.ac", fontSize = 11.5.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Password / SSO Token", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                onRoleSelect(role)
                if (uiState.authMode == AuthMode.SIGN_UP) {
                    onSignUp(email, password)
                }
                else {
                    onSignIn(email, password)
                }
            },
            enabled = !uiState.isAuthLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
        ) {
            if (uiState.isAuthLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            }
            else {
                Text(
                    if (uiState.authMode == AuthMode.SIGN_UP) {
                        "Create Account"
                    }
                    else {
                        "Continue with Institutional Login ->"
                    }, fontWeight = FontWeight.Bold, fontSize = 13.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onToggleAuthMode) {
            Text(
                if (uiState.authMode == AuthMode.SIGN_UP) {
                    "Already have an Account? Sign In"
                }
                else {
                    "New to TutorBridge? Create an Account"
                }, fontSize = 12.sp
            )
        }

        uiState.authErrorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                onRoleSelect(role)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("G  ", fontWeight = FontWeight.Black, fontSize = 15.sp, color = CrimsonPrimary)
            Text("Continue with Google Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Integrated with South African Higher Education SSO • POPIA Compliant",
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    TutorBridgeTheme {
        AuthScreen(
            uiState = TutorBridgeUiState(),
            onRoleSelect = {},
            onSignIn = { _, _ -> },
            onSignUp = {_, _ -> },
            onToggleAuthMode = {}
        )
    }
}
