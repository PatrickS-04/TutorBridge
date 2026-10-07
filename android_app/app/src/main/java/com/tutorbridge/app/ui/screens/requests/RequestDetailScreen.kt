package com.tutorbridge.app.ui.screens.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.RequestStatus
import com.tutorbridge.app.data.model.StudentRequestUi
import com.tutorbridge.app.data.model.TutorApplicant
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.EmeraldTint
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun RequestDetailScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onAcceptApplicant: (TutorApplicant) -> Unit,
    onEdit: (StudentRequestUi) -> Unit = {},
    onClose: (StudentRequestUi) -> Unit = {},
    onWithdraw: (StudentRequestUi) -> Unit = {}
) {
    val req = uiState.selectedRequest ?: return
    // Which of the two destructive actions is waiting on a yes.
    var confirming by remember { mutableStateOf<RequestEnd?>(null) }
    val canEdit = req.status == RequestStatus.OPEN
    val canEnd = req.status == RequestStatus.OPEN || req.status == RequestStatus.MATCHED

    confirming?.let { end ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (end == RequestEnd.WITHDRAW) "Withdraw this request?" else "Close this request?") },
            text = {
                Text(
                    if (end == RequestEnd.WITHDRAW) {
                        "Tutors who offered will be told it is no longer available. You can't undo this."
                    } else {
                        "Mark it as sorted. Tutors who offered will be told. You can't undo this."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    if (end == RequestEnd.WITHDRAW) onWithdraw(req) else onClose(req)
                }) { Text(if (end == RequestEnd.WITHDRAW) "Withdraw" else "Close", color = CrimsonPrimary) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Keep it") } }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text("Request Detail", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("${req.subject} (${req.courseCode}) — ${req.topic}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                when (req.status) {
                                    RequestStatus.OPEN -> "Open"
                                    RequestStatus.MATCHED -> "Matched"
                                    RequestStatus.WITHDRAWN -> "Withdrawn"
                                    else -> "Closed"
                                },
                                color = CrimsonPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    DetailLine("Level:", req.level)
                    DetailLine("Time Window:", req.timeWindow)
                    DetailLine("Location:", req.preferredVenue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "\"${req.description}\"",
                        fontSize = 12.5.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    )
                }
            }
        }

        if (canEnd) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canEdit) {
                        OutlinedButton(
                            onClick = { onEdit(req) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) { Text("Edit request", fontWeight = FontWeight.Bold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { confirming = RequestEnd.CLOSE },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) { Text("Close", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { confirming = RequestEnd.WITHDRAW },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) { Text("Withdraw", fontWeight = FontWeight.Bold, color = CrimsonPrimary) }
                    }
                }
            }
        }

        item {
            Text("Applicants (${req.applicants.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(req.applicants) { applicant ->
            // A request that is no longer open has nobody left to accept.
            ApplicantCard(applicant, canAccept = canEdit) { onAcceptApplicant(applicant) }
        }
    }
}

private enum class RequestEnd { CLOSE, WITHDRAW }

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.5.sp)
    }
}

@Composable
private fun ApplicantCard(applicant: TutorApplicant, canAccept: Boolean, onAccept: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(applicant.initials, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Column {
                    Text(applicant.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (applicant.verified) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(12.dp))
                            Text("Verified Institute Mentor", fontSize = 12.sp, color = EmeraldSuccess, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                "\"${applicant.note}\"",
                fontSize = 12.5.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAccept,
                enabled = canAccept,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text("Accept & Match", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RequestDetailScreenPreview() {
    TutorBridgeTheme {
        RequestDetailScreen(
            uiState = TutorBridgeUiState(),
            onBack = {},
            onAcceptApplicant = {}
        )
    }
}
