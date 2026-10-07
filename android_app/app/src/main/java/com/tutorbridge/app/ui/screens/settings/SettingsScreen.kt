package com.tutorbridge.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import com.tutorbridge.app.R

@Composable
fun SettingsScreen(
    uiState: TutorBridgeUiState,
    onToggleDark: (Boolean) -> Unit,
    onToggleOffline: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onDeleteAccountClick: () -> Unit,
    onSignOut: () -> Unit,
    onEditProfileClick: () -> Unit = {},
    onToggleBookingUpdates: (Boolean) -> Unit = {},
    onToggleSessionReminders: (Boolean) -> Unit = {},
    onToggleSmsFallback: (Boolean) -> Unit = {},
    onToggleBiometric: (Boolean) -> Unit = {},
    onLinkCalendar: () -> Unit = {},
    onUnlinkCalendar: () -> Unit = {},
    onChangePassword: ((String) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val isTutor = uiState.role == UserRole.TUTOR
    var passwordMessage by remember { mutableStateOf<String?>(null) }
    var showLanguageDialog by remember {mutableStateOf(false) }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("App Language") },
            text = {
                Column {
                    listOf("English", "isiZulu", "Afrikaans").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectLanguage(option)
                                    showLanguageDialog = false
                                    (context as? android.app.Activity)?.recreate()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.selectedLanguage == option, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Account Profile Row Card
        item {
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
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTutor) "CV" else "LN",
                            fontWeight = FontWeight.Bold,
                            color = NavySecondary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.userName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = uiState.userEmail,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEditProfileClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Notifications & Language Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        label = stringResource(R.string.app_language_label),
                        trailing = { Text("${uiState.selectedLanguage} ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                        onClick = { showLanguageDialog = true }
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.Notifications,
                        label = stringResource(R.string.booking_updates_label),
                        checked = uiState.bookingUpdatesEnabled,
                        onCheckedChange = onToggleBookingUpdates
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.Notifications,
                        label = stringResource(R.string.session_reminders_label),
                        checked = uiState.sessionRemindersEnabled,
                        onCheckedChange = onToggleSessionReminders
                    )
                }
            }
        }

        // Google Calendar - student or tutor may link one. The event is written
        // to whichever calendar is linked and the other party is invited to it.
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.CalendarMonth,
                        label = stringResource(R.string.calendar_title),
                        trailing = {
                            when {
                                uiState.isCalendarLinking -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                uiState.calendarLinked -> TextButton(onClick = onUnlinkCalendar) { Text(stringResource(R.string.calendar_disconnect)) }
                                else -> TextButton(onClick = onLinkCalendar) { Text(stringResource(R.string.calendar_connect)) }
                            }
                        },
                        onClick = {}
                    )
                    val status = uiState.calendarMessage
                        //A failed write outranks the happy "Connected as" line:
                        //it is the one thing the row could say that the user
                        //cannot find out any other way.
                        ?: uiState.calendarSyncError?.let { "Last sync failed: $it" }
                        ?: uiState.calendarAccountEmail?.let { "Connected as $it" }
                        ?: stringResource(R.string.calendar_default_status)
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    )
                }
            }
        }

        // Security Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Lock,
                        label = stringResource(R.string.change_password_label),
                        trailing = { TextButton(onClick = { onChangePassword { passwordMessage = it } }) { Text(stringResource(R.string.email_link_action)) } },
                        onClick = {}
                    )
                    passwordMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        )
                    }
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.Shield,
                        label = stringResource(R.string.sms_fallback_label),
                        checked = uiState.smsFallbackEnabled,
                        onCheckedChange = onToggleSmsFallback,
                        tint = CrimsonPrimary
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.Shield,
                        label = stringResource(R.string.biometric_label),
                        checked = uiState.biometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        tint = CrimsonPrimary
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.DarkMode,
                        label = stringResource(R.string.dark_theme_label),
                        checked = uiState.isDarkTheme,
                        onCheckedChange = onToggleDark
                    )
                }
            }
        }

        // Sign Out Button
        item {
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    // Navy text on the dark navy surface is all but invisible, so dark
                    // mode gets the brand crimson behind it instead.
                    containerColor = if (uiState.isDarkTheme) CrimsonPrimary else MaterialTheme.colorScheme.surface,
                    contentColor = if (uiState.isDarkTheme) MaterialTheme.colorScheme.onPrimary else NavySecondary
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out_action), fontWeight = FontWeight.Bold)
            }
        }

        // Student-Only Delete Account Action
        if (!isTutor) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = onDeleteAccountClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonPrimary)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.delete_account_action), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.delete_account_subtitle),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
        trailing()
    }
}

@Composable
private fun SettingsCheckRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    TutorBridgeTheme {
        SettingsScreen(
            uiState = TutorBridgeUiState(),
            onToggleDark = {},
            onToggleOffline = {},
            onSelectLanguage = {},
            onDeleteAccountClick = {},
            onSignOut = {}
        )
    }
}
