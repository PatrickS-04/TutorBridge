package com.tutorbridge.app.ui.screens.auth

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.AuthMode
import com.tutorbridge.app.data.model.GoogleAuthentication
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.NavyTint
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import kotlinx.coroutines.launch
import com.tutorbridge.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    uiState: TutorBridgeUiState,
    onRoleSelect: (UserRole) -> Unit,
    onInstitutionSelect: (String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit, // fullName, email, password
    onGoogleSignIn: (String) -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onToggleAuthMode: () -> Unit,
    onForgotPassword: (String) -> Unit = {}
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var institutionDropdownExpanded by remember { mutableStateOf(false) }
    // Role is inferred from the email pattern in the real prototype;
    // default to student unless the user has already picked tutor elsewhere in the app.
    val role = uiState.role ?: UserRole.STUDENT
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isSignUp = uiState.authMode == AuthMode.SIGN_UP
    val selectedInstitutionName = uiState.institutionOptions
        .firstOrNull { it.id == uiState.selectedSignupInstitutionId }
        ?.name ?: uiState.institutionName

    val canSubmit = !uiState.isAuthLoading && email.isNotBlank() && password.isNotBlank() &&
        (!isSignUp || (fullName.isNotBlank() && uiState.selectedSignupInstitutionId != null))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Image(
            painter = painterResource(R.drawable.ic_tutorbridge_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = NavySecondary)
                Text(stringResource(R.string.auth_higher_education_tutoring_networ), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Text(stringResource(R.string.auth_sa_campuses), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Institutional Partner - a real dropdown backed by GET /reference/institutions.
        // Sign-up only: an existing account already carries its institution, which
        // the server resolves on sign-in.
        if (isSignUp) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.auth_institutional_partner), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = institutionDropdownExpanded,
                    onExpandedChange = { institutionDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedInstitutionName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = institutionDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = institutionDropdownExpanded,
                        onDismissRequest = { institutionDropdownExpanded = false }
                    ) {
                        if (uiState.institutionOptions.isEmpty()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.auth_loading_institutions)) }, onClick = {}, enabled = false)
                        }
                        uiState.institutionOptions.forEach { institution ->
                            DropdownMenuItem(
                                text = { Text(institution.name) },
                                onClick = {
                                    onInstitutionSelect(institution.id)
                                    institutionDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Role-sorted access info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavyTint)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(15.dp))
                    Text(stringResource(R.string.auth_role_sorted_campus_access), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = NavySecondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.auth_students_sign_in_with_your),
                    fontSize = 12.sp,
                    color = NavySecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.auth_tutors_log_in_with_your),
                    fontSize = 12.sp,
                    color = NavySecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSignUp) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.auth_full_name), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    placeholder = { Text(stringResource(R.string.auth_e_g_liam_naidoo), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.auth_campus_email), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text(stringResource(R.string.auth_e_g_ndxlia003_myuct_ac), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.auth_password), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                // Without KeyboardType.Password the IME treats a random temp
                // password like a dictionary word - autocorrect/autocapitalize
                // can silently mangle it before it ever reaches Firebase.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    autoCorrect = false
                ),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }

        // Sign-in only: a brand-new account has no password to forget.
        if (!isSignUp) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onForgotPassword(email) }, enabled = !uiState.isAuthLoading) {
                    Text(stringResource(R.string.auth_forgot_password), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Spacer(modifier = Modifier.height(18.dp))
        }

        Button(
            onClick = {
                onRoleSelect(role)
                if (isSignUp) {
                    onSignUp(fullName, email, password)
                } else {
                    onSignIn(email, password)
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
        ) {
            if (uiState.isAuthLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(
                    if (isSignUp) stringResource(R.string.auth_create_account) else stringResource(R.string.auth_login),
                    fontWeight = FontWeight.Bold, fontSize = 13.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onToggleAuthMode) {
            Text(
                if (isSignUp) stringResource(R.string.auth_already_have_an_account_sign) else stringResource(R.string.auth_new_to_tutorbridge_create_an),
                fontSize = 12.sp
            )
        }

        uiState.authErrorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        uiState.authInfoMessage?.let { message ->
            Text(message, color = NavySecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        val idToken = GoogleAuthentication.idTokenRequest(context, context.getString(R.string.google_web_client_id))
                        onRoleSelect(role)
                        onGoogleSignIn(idToken)
                    } catch (exception: Exception) {
                        onGoogleSignInError(exception.message ?: context.getString(R.string.auth_google_sign_in_failed))
                    }
                }
            },
            enabled = !uiState.isAuthLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Image(painter = painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.auth_continue_with_google_account), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            stringResource(R.string.auth_integrated_with_south_african_hi),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/**
 * Where a tutor lands after signing in with the temporary password their
 * institution admin issued. Nothing else opens until it is replaced.
 */
@Composable
fun NewPasswordScreen(
    uiState: TutorBridgeUiState,
    onSubmit: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    // Firebase's minimum; the server checks it again, and that it is not the temporary one.
    val canSubmit = !uiState.isAuthLoading && password.length >= 6 && password == confirm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.ic_tutorbridge_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.auth_set_a_new_password), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = NavySecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.auth_you_signed_in_with_a),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.auth_new_password_at_least_6), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    autoCorrect = false
                ),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.auth_confirm_new_password), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                isError = confirm.isNotEmpty() && confirm != password,
                supportingText = if (confirm.isNotEmpty() && confirm != password) {
                    { Text(stringResource(R.string.auth_the_passwords_do_not_match)) }
                } else null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    autoCorrect = false
                ),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = { onSubmit(password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavySecondary)
        ) {
            if (uiState.isAuthLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.auth_save_password), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }

        uiState.authErrorMessage?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onSignOut, enabled = !uiState.isAuthLoading) {
            Text(stringResource(R.string.common_sign_out), fontSize = 12.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NewPasswordScreenPreview() {
    TutorBridgeTheme {
        NewPasswordScreen(uiState = TutorBridgeUiState(), onSubmit = {}, onSignOut = {})
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    TutorBridgeTheme {
        AuthScreen(
            uiState = TutorBridgeUiState(),
            onRoleSelect = {},
            onInstitutionSelect = {},
            onSignIn = { _, _ -> },
            onSignUp = { _, _, _ -> },
            onGoogleSignIn = { _ -> },
            onGoogleSignInError = { _ -> },
            onToggleAuthMode = {}
        )
    }
}
