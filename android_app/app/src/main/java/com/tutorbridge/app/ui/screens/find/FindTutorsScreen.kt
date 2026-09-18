package com.tutorbridge.app.ui.screens.find

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import com.tutorbridge.app.data.model.TutorUi
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.EmeraldSuccess
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeUiState

@Composable
fun FindTutorsScreen(
    uiState: TutorBridgeUiState,
    onSelectTutor: (TutorUi) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeNowOnly by remember { mutableStateOf(false) }
    var topRatedOnly by remember { mutableStateOf(false) }

    var filteredTutors = uiState.tutors.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.subject.contains(searchQuery, ignoreCase = true) ||
        it.courseCode.contains(searchQuery, ignoreCase = true)
    }
    if (activeNowOnly) filteredTutors = filteredTutors.filter { it.activeNow }
    if (topRatedOnly) filteredTutors = filteredTutors.filter { it.rating >= 4.9 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Find a Campus Tutor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Explore appointed faculty tutors & undergraduate teaching assistants.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search course code or subject (e.g. MAM)", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeNowOnly,
                    onClick = { activeNowOnly = !activeNowOnly },
                    label = { Text("Active on campus now", fontSize = 11.5.sp) }
                )
                FilterChip(
                    selected = topRatedOnly,
                    onClick = { topRatedOnly = !topRatedOnly },
                    label = { Text("★ Top Rated (4.9+)", fontSize = 11.5.sp) }
                )
            }
        }

        items(filteredTutors) { tutor ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectTutor(tutor) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text(tutor.initials, fontWeight = FontWeight.Bold) }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tutor.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                Text("${tutor.matchPercent}% match", color = CrimsonPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("${tutor.subject}\n(${tutor.courseCode})", fontSize = 11.5.sp, color = CrimsonPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (tutor.activeNow) "Active now" else "Active today",
                                fontSize = 11.sp,
                                color = if (tutor.activeNow) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Text("  •  ★ ${tutor.rating}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(tutor.staffOrTutorId, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FindTutorsScreenPreview() {
    TutorBridgeTheme {
        FindTutorsScreen(
            uiState = TutorBridgeUiState(),
            onSelectTutor = {}
        )
    }
}
