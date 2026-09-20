package com.tutorbridge.app.ui.screens.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.data.model.RequestStatus
import com.tutorbridge.app.data.model.StudentRequestUi
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.EmeraldTint
import com.tutorbridge.app.ui.theme.NavyTint
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun MyRequestsScreen(
    uiState: TutorBridgeUiState,
    onNewRequest: () -> Unit,
    onSelectRequest: (StudentRequestUi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("My Requests", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Track requests you've submitted and review mentor applicants.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onNewRequest,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(uiState.myRequests) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectRequest(req) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "${req.subject} (${req.courseCode}) — ${req.topic}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                            StatusBadge(req.status)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${req.level} • ${req.timeWindow}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (req.preferredVenue.isNotBlank()) {
                            Text("📍 ${req.preferredVenue}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        when (req.status) {
                            RequestStatus.OPEN -> {
                                if (req.applicantCount > 0) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NavyTint)
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Group, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(16.dp))
                                        Text(
                                            "${req.applicantCount} tutor applicants — Tap to review & match",
                                            fontSize = 12.sp,
                                            color = NavySecondary
                                        )
                                    }
                                }
                            }
                            RequestStatus.MATCHED -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EmeraldTint)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        if (req.matchedTutorName.isBlank()) "Matched" else "Matched with ${req.matchedTutorName}",
                                        fontSize = 12.sp,
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: RequestStatus) {
    val (label, bg, fg) = when (status) {
        RequestStatus.OPEN -> Triple("Open Request", MaterialTheme.colorScheme.primaryContainer, CrimsonPrimary)
        RequestStatus.MATCHED -> Triple("✓ Matched", EmeraldTint, EmeraldSuccess)
        RequestStatus.OFFERED -> Triple("Offered", EmeraldTint, EmeraldSuccess)
        RequestStatus.COMPLETED -> Triple("Completed", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Badge(containerColor = bg) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
    }
}

@Preview(showBackground = true)
@Composable
fun MyRequestsScreenPreview() {
    TutorBridgeTheme {
        MyRequestsScreen(
            uiState = TutorBridgeUiState(),
            onNewRequest = {},
            onSelectRequest = {}
        )
    }
}
