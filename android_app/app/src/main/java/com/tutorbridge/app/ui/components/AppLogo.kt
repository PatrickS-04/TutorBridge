package com.tutorbridge.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.tutorbridge.app.R

/**
 * The TutorBridge bridge icon, in its light or dark version to suit the theme. Both are flat
 * squares in the app's cream or navy, so the corners are rounded here rather than in the picture.
 */
@Composable
fun AppLogo(size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(if (dark) R.drawable.logo_dark else R.drawable.logo_light),
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f))
    )
}
