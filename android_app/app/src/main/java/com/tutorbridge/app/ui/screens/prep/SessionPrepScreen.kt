package com.tutorbridge.app.ui.screens.prep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun SessionPrepScreen(
    uiState: TutorBridgeUiState,
    onBack: () -> Unit,
    onToggleGoal: (String) -> Unit = {}
) {
    val completed = uiState.sessionGoals.count { it.completed }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Campus Session Prep",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Header Card: badge row + participant + venue
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavySecondary)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = Color.White.copy(alpha = 0.15f)) {
                            Text("Starts in 15 mins", color = Color.White, fontSize = 11.sp)
                        }
                        Text("Upper Campus Room 2.10", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("LN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Column {
                            Text("Liam Naidoo (NDXLIA003)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("MAM1000W Calculus • 10:30 AM (1.5 hrs)", color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = "Venue", tint = CrimsonPrimary, modifier = Modifier.size(16.dp))
                        Text("Upper Campus — Leslie Social Sciences Room 2.10", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Session Goals Checklist
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Goals Checklist", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("$completed/${uiState.sessionGoals.size} completed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    uiState.sessionGoals.forEach { goal ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = goal.completed, onCheckedChange = { onToggleGoal(goal.id) })
                            Text(
                                goal.text,
                                fontSize = 13.sp,
                                textDecoration = if (goal.completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (goal.completed) CrimsonPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Attached Study Materials
        item {
            Text("Attached Study Materials", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(uiState.studyMaterials) { material ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = "PDF", tint = CrimsonPrimary)
                    Text("${material.fileName} (${material.sizeLabel})", fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SessionPrepScreenPreview() {
    TutorBridgeTheme {
        SessionPrepScreen(
            uiState = TutorBridgeUiState(),
            onBack = {}
        )
    }
}
