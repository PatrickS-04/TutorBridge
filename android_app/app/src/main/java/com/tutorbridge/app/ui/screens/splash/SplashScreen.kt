package com.tutorbridge.app.ui.screens.splash

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R
import com.tutorbridge.app.ui.theme.CanvasCream
import com.tutorbridge.app.ui.theme.NavySecondary
import com.tutorbridge.app.ui.theme.TutorBridgeTheme

/**
 * Welcome screen. The artwork (bridge, name, student and book) comes as a light and a dark
 * picture, each with its own flat background, so the screen is painted in that same colour
 * instead of the theme's, or a seam shows round the picture.
 */
@Composable
fun SplashScreen(
    onGetStarted: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val artwork = if (dark) R.drawable.splash_dark else R.drawable.splash_light
    val canvas = if (dark) NavySecondary else CanvasCream

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        )

        Text(
            text = stringResource(R.string.splash_institutional_tutoring_departmen),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (dark) CanvasCream.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                // Navy on the navy canvas would vanish, so the dark splash gets a cream button.
                containerColor = if (dark) CanvasCream else NavySecondary,
                contentColor = if (dark) NavySecondary else MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Text(
                text = stringResource(R.string.splash_campus_sign_in),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    TutorBridgeTheme {
        SplashScreen(
            onGetStarted = {}
        )
    }
}
