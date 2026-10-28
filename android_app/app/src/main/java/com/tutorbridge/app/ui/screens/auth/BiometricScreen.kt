package com.tutorbridge.app.ui.screens.auth

import com.tutorbridge.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BiometricScreen(
    onUnlockClick: () -> Unit,
    onSignInDifferently: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.biometric_welcome_back), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.biometric_confirm_it_s_you_to), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onUnlockClick, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text(stringResource(R.string.biometric_unlock))
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onSignInDifferently) {
            Text(stringResource(R.string.biometric_sign_in_with_a_different))
        }
    }
}