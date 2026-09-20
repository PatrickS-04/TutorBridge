package com.tutorbridge.app.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun SettingsScreen(
    uiState: TutorBridgeUiState,
    onToggleDark: (Boolean) -> Unit,
    onToggleOffline: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onDeleteAccountClick: () -> Unit,
    onSignOut: () -> Unit,
    onToggleBookingUpdates: (Boolean) -> Unit = {},
    onToggleSessionReminders: (Boolean) -> Unit = {},
    onToggleSmsFallback: (Boolean) -> Unit = {},
    onToggleBiometric: (Boolean) -> Unit = {},
    onLinkCalendar: () -> Unit = {},
    onUnlinkCalendar: () -> Unit = {},
    onChangePassword: ((String) -> Unit) -> Unit = {}
) {
    val isTutor = uiState.role == UserRole.TUTOR
    var passwordMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Preferences, notifications, and application controls.",
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
                    IconButton(onClick = {}) {
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
                        label = "App Language",
                        trailing = { Text("${uiState.selectedLanguage} ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
                        onClick = {}
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.Notifications,
                        label = "Booking Updates",
                        checked = uiState.bookingUpdatesEnabled,
                        onCheckedChange = onToggleBookingUpdates
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.Notifications,
                        label = "Session Reminders",
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
                        label = "Google Calendar",
                        trailing = {
                            when {
                                uiState.isCalendarLinking -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                uiState.calendarLinked -> TextButton(onClick = onUnlinkCalendar) { Text("Disconnect") }
                                else -> TextButton(onClick = onLinkCalendar) { Text("Connect") }
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
                        ?: "Confirmed sessions are added to your calendar automatically."
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
                        label = "Change password",
                        trailing = { TextButton(onClick = { onChangePassword { passwordMessage = it } }) { Text("Email me a link") } },
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
                        label = "SMS Fallback for OTP",
                        checked = uiState.smsFallbackEnabled,
                        onCheckedChange = onToggleSmsFallback,
                        tint = CrimsonPrimary
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.Shield,
                        label = "Biometric Sign-in",
                        checked = uiState.biometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        tint = CrimsonPrimary
                    )
                    Divider()
                    SettingsCheckRow(
                        icon = Icons.Default.DarkMode,
                        label = "Dark Theme",
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
                Text("Sign Out", fontWeight = FontWeight.Bold)
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
                        Text("Delete Student Account", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Permanently purge student profile, requests, and campus bookings.",
                        fontSize = 11.sp,
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
