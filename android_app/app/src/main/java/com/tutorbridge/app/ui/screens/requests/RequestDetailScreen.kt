package com.tutorbridge.app.ui.screens.requests

import com.tutorbridge.app.R
import androidx.compose.ui.res.stringResource
import com.tutorbridge.app.ui.components.NeedsConnectionHint
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
    // Whether a request is still open is the server's to say, so changing one needs a connection.
    val online = uiState.isOnline
    val canEnd = req.status == RequestStatus.OPEN || req.status == RequestStatus.MATCHED

    confirming?.let { end ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (end == RequestEnd.WITHDRAW) stringResource(R.string.request_detail_withdraw_this_request) else stringResource(R.string.request_detail_close_this_request)) },
            text = {
                Text(
                    if (end == RequestEnd.WITHDRAW) {
                        stringResource(R.string.request_detail_tutors_who_offered_will_be)
                    } else {
                        stringResource(R.string.request_detail_mark_it_as_sorted_tutors)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    if (end == RequestEnd.WITHDRAW) onWithdraw(req) else onClose(req)
                }) { Text(if (end == RequestEnd.WITHDRAW) stringResource(R.string.request_detail_withdraw) else stringResource(R.string.request_detail_close), color = CrimsonPrimary) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.request_detail_keep_it)) } }
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
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(stringResource(R.string.request_detail_request_detail), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                                    RequestStatus.OPEN -> stringResource(R.string.tutor_home_open)
                                    RequestStatus.MATCHED -> stringResource(R.string.common_matched)
                                    RequestStatus.WITHDRAWN -> stringResource(R.string.common_withdrawn)
                                    else -> stringResource(R.string.common_closed)
                                },
                                color = CrimsonPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    DetailLine(stringResource(R.string.request_detail_level), req.level)
                    DetailLine(stringResource(R.string.request_detail_time_window), req.timeWindow)
                    DetailLine(stringResource(R.string.request_detail_location), req.preferredVenue)
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
                            enabled = online,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) { Text(stringResource(R.string.request_detail_edit_request), fontWeight = FontWeight.Bold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { confirming = RequestEnd.CLOSE },
                            enabled = online,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) { Text(stringResource(R.string.request_detail_close), fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { confirming = RequestEnd.WITHDRAW },
                            enabled = online,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) { Text(stringResource(R.string.request_detail_withdraw), fontWeight = FontWeight.Bold, color = CrimsonPrimary) }
                    }
                    NeedsConnectionHint(online)
                }
            }
        }

        item {
            Text(stringResource(R.string.request_detail_applicants, req.applicants.size), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(req.applicants) { applicant ->
            // A request that is no longer open has nobody left to accept.
            ApplicantCard(applicant, canAccept = canEdit && online) { onAcceptApplicant(applicant) }
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
                            Text(stringResource(R.string.request_detail_verified_institute_mentor), fontSize = 12.sp, color = EmeraldSuccess, fontWeight = FontWeight.Bold)
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text(stringResource(R.string.request_detail_accept_match), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
