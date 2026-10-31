package com.tutorbridge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R
import com.tutorbridge.app.ui.theme.CrimsonPrimary
import com.tutorbridge.app.ui.theme.CrimsonTint

/**
 * "Weekly 2 of 4": marks a session that belongs to a weekly series and says where in it this one falls.
 * Nothing is drawn for a single session, so a caller can place it unconditionally.
 * [onDark] is for the navy header of the session prep screen.
 */
@Composable
fun SeriesBadge(index: Int, count: Int, modifier: Modifier = Modifier, onDark: Boolean = false) {
    if (index < 1 || count < 2) return
    Text(
        text = stringResource(R.string.series_badge, index, count),
        color = if (onDark) Color.White else CrimsonPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (onDark) Color.White.copy(alpha = 0.15f) else CrimsonTint)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
