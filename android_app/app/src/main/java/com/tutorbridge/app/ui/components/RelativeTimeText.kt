package com.tutorbridge.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tutorbridge.app.R
import com.tutorbridge.app.data.model.RelativeTime
import com.tutorbridge.app.data.model.relativeTimeOf

/** "Just now", "5 minutes ago", "Yesterday"... in the language the app is showing. Empty for a missing timestamp. */
@Composable
fun relativeTimeText(iso: String?): String = when (val time = relativeTimeOf(iso)) {
    null -> ""
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes_ago, time.count.toInt(), time.count)
    is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours_ago, time.count.toInt(), time.count)
    RelativeTime.Yesterday -> stringResource(R.string.time_yesterday)
    is RelativeTime.Days -> pluralStringResource(R.plurals.time_days_ago, time.count.toInt(), time.count)
}
