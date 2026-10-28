package com.tutorbridge.app.ui.screens.claims

import com.tutorbridge.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.ClaimStatus
import com.tutorbridge.app.viewmodel.CLAIM_MONTHS_MAX
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.EmeraldTint
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun TutorExportClaimsScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onSelectInstitution: (String) -> Unit = {},
    onSelectClaimPeriod: (String) -> Unit = {},
    onLoadEarlier: () -> Unit = {},
    onFetchCsv: (month: String, onResult: (Result<String>) -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val institution = uiState.institutions.find { it.id == uiState.selectedInstitutionId }
        ?: uiState.institutions.firstOrNull()
    val period = institution?.claimPeriods?.find { it.id == uiState.selectedClaimPeriodId }
        ?: institution?.claimPeriods?.firstOrNull()

    // Nothing claimable yet: no completed sessions on record.
    if (institution == null || period == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.claims_no_claim_periods_yet), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.claims_completed_sessions_show_up_here),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Column {
                    Text(stringResource(R.string.common_timesheets_claims), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.claims_institution_payment_export), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Institution card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(NavySecondary),
                        contentAlignment = Alignment.Center
                    ) { Text(institution.shortName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(institution.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(stringResource(R.string.claims_faculty_staff, institution.faculty, institution.staffId), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.claims_rate_per_hour, institution.rate.toInt()), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(stringResource(R.string.claims_rate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Claim period pills
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                institution.claimPeriods.forEach { p ->
                    val selected = p.id == period.id
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) NavySecondary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelectClaimPeriod(p.id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            p.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (p.status == ClaimStatus.PAID) stringResource(R.string.claims_paid) else stringResource(R.string.tutor_home_active),
                            fontSize = 12.sp,
                            color = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Only offered while the API has more history to give.
        if (uiState.claimMonthsLoaded < CLAIM_MONTHS_MAX) {
            item {
                TextButton(onClick = onLoadEarlier) {
                    Text(stringResource(R.string.claims_load_earlier_months), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Claim period detail card
        item {
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
                        Column {
                            Text(stringResource(R.string.claims_claim_period), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(period.label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        if (period.status == ClaimStatus.PAID) {
                            Badge(containerColor = EmeraldTint) {
                                Text(stringResource(R.string.claims_paid_to_bank), color = EmeraldSuccess, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                Text(stringResource(R.string.claims_draft_claimable), color = CrimsonPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock(stringResource(R.string.claims_total_hours), stringResource(R.string.claims_hours_value, period.totalHours.toString()))
                        StatBlock(stringResource(R.string.claims_sessions), "${period.sessionsCount}")
                        StatBlock(stringResource(R.string.claims_claim_value), "R${period.claimValue.toInt()}", emphasized = true)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (period.claimRef.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.claims_claim_ref, period.claimRef), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.claims_copy_ref), color = CrimsonPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { exportClaimVoucher(context, institution, period) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.claims_pdf_voucher), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                // The server builds the CSV; the period id is its YYYY-MM month.
                                onFetchCsv(period.id) { result ->
                                    result.onSuccess { csv -> shareClaimCsv(context, institution, period, csv) }
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.claims_csv_export), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (period.status == ClaimStatus.PAID) {
                            OutlinedButton(
                                onClick = {},
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.claims_claim_settled), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.claims_submit_to_admin), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Itemized sessions
        if (period.sessions.isNotEmpty()) {
            item {
                Column {
                    Text(stringResource(R.string.claims_itemized_sessions, period.sessionsCount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(stringResource(R.string.claims_verified_with_student_attendance), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(period.sessions) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Badge(containerColor = NavySecondary) {
                                Text(session.courseCode, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(session.amount, fontWeight = FontWeight.Bold, color = CrimsonPrimary, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${session.date} • ${session.timeRange}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("${session.studentName} (${session.studentIdTag})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (session.topic.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.claims_topic, session.topic), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.claims_hours_at_rate, session.hours.toString(), institution.rate.toInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, emphasized: Boolean = false) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = if (emphasized) 16.sp else 15.sp,
            color = if (emphasized) CrimsonPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TutorExportClaimsScreenPreview() {
    TutorBridgeTheme {
        TutorExportClaimsScreen(
            uiState = TutorBridgeUiState(),
            onBack = {}
        )
    }
}
