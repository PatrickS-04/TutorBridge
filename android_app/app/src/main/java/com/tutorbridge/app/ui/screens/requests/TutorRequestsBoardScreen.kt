package com.tutorbridge.app.ui.screens.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun TutorRequestsBoardScreen(
    uiState: TutorBridgeUiState,
    onAcceptAndOffer: (String) -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 = Open Requests, 1 = My Applications

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Student Requests", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Browse tuition requests posted by campus students.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Segmented tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            SegTab("Open Requests (${uiState.openRequests.size})", tab == 0, Modifier.weight(1f)) { tab = 0 }
            SegTab("My Applications (${uiState.myApplications.size})", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }

        val listToShow = if (tab == 0) uiState.openRequests else uiState.myApplications

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(listToShow) { req ->
                if (tab == 0) {
                    OpenRequestCard(req, onAcceptAndOffer)
                } else {
                    MyApplicationCard(req)
                }
            }
        }
    }
}

@Composable
private fun SegTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) NavySecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun OpenRequestCard(req: StudentRequestUi, onAcceptAndOffer: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(req.studentInitials, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Column {
                    Text(req.studentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(req.subject, color = CrimsonPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("🕐 ${req.timeWindow} • 📍 ${req.preferredVenue}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "\"${req.topic}\"",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp)
            )
            Button(
                onClick = { onAcceptAndOffer(req.id) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text("Accept & Offer Session (Department Rate: R220/hr)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MyApplicationCard(req: StudentRequestUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(req.subject, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Student: ${req.studentName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Badge(containerColor = EmeraldTint) {
                Text("Offered", color = EmeraldSuccess, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TutorRequestsBoardScreenPreview() {
    TutorBridgeTheme {
        TutorRequestsBoardScreen(
            uiState = TutorBridgeUiState(),
            onAcceptAndOffer = {}
        )
    }
}
