package com.tutorbridge.app.ui.components

import com.tutorbridge.app.R
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A booking's venue as a tappable line that opens the Google Maps app on it, where
 * the tutor can pan, zoom and start directions. Maps searches the venue text itself,
 * so no geocoding or API key is needed on this side.
 */
@Composable
fun VenueLink(venue: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    if (venue.isBlank()) return
    val context = LocalContext.current
    Text(
        "📍 $venue",
        modifier = modifier.clickable {
            // Reference: Android Developers. 2026. Google Maps intents for Android. [Online].
            //   Available at: https://developer.android.com/guide/components/google-maps-intents [Accessed 21 September 2026].
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(venue)}"))
            runCatching { context.startActivity(intent) }
                .onFailure { Toast.makeText(context, context.getString(R.string.common_no_maps_app), Toast.LENGTH_SHORT).show() }
        },
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )
}
