package com.tutorbridge.app.ui.screens.calendar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tutorbridge.app.R
import com.tutorbridge.app.data.model.CalendarEventUi
import com.tutorbridge.app.data.model.CheckInCodeUi
import com.tutorbridge.app.ui.components.QrCode
import com.tutorbridge.app.viewmodel.canCheckInNow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

private val CheckedInGreen = Color(0xFF2E7D32)

// Tutor: the QR code and digits to show the student, with a live countdown
@Composable
fun CheckInCodeDialog(code: CheckInCodeUi, onNewCode: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(code.code) {
        while (true) { now = Instant.now(); delay(1_000) }
    }

    val secondsLeft = Duration.between(now, code.expiresAt).seconds.coerceAtLeast(0)
    val expired = secondsLeft == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_show_code_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.checkin_show_code_hint), fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.size(220.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (expired) {
                        Text(stringResource(R.string.checkin_code_expired), color = Color.Black, textAlign = TextAlign.Center)
                    } else {
                        QrCode(code.qrPayload, stringResource(R.string.checkin_qr_description), Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    code.code.chunked(3).joinToString(" "),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (expired) stringResource(R.string.checkin_code_expired)
                    else stringResource(R.string.checkin_code_expires_in, secondsLeft / 60, secondsLeft % 60),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onNewCode) { Text(stringResource(R.string.checkin_new_code)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.checkin_close)) } }
    )
}

// Student: type the 6 digits when scanning is not possible
@Composable
fun EnterCheckInCodeDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_enter_code_title)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { input -> code = input.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.checkin_code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(code) }, enabled = code.length == 6) {
                Text(stringResource(R.string.checkin_check_in))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.checkin_close)) } }
    )
}

fun scanCheckInQr(context: Context, onResult: (String?) -> Unit, onUnavailable: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> onResult(barcode.rawValue) }
        .addOnFailureListener { onUnavailable() }
}

@Composable
fun CheckInRow(
    event: CalendarEventUi,
    isTutor: Boolean,
    enabled: Boolean,
    onShowCode: () -> Unit,
    onScan: () -> Unit,
    onEnterCode: () -> Unit
) {
    when {
        event.checkedIn -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Verified, contentDescription = null, tint = CheckedInGreen, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.checkin_checked_in_at, event.checkedInTime),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CheckedInGreen
            )
        }
        !event.canCheckInNow() -> Unit
        isTutor -> Button(onClick = onShowCode, enabled = enabled, shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.checkin_show_code), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScan, enabled = enabled, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.checkin_scan), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onEnterCode, enabled = enabled) {
                Text(stringResource(R.string.checkin_enter_code), fontSize = 12.sp)
            }
        }
    }
}