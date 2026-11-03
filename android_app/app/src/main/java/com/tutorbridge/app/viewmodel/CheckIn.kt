package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.model.CalendarEventUi
import java.net.URI
import java.time.Duration
import java.time.Instant

val CHECK_IN_OPENS_BEFORE: Duration = Duration.ofMinutes(15)

fun CalendarEventUi.canCheckInNow(now: Instant = Instant.now()): Boolean {
    if (!isConfirmed || checkedIn)
    {
        return false
    }

    val start = runCatching { Instant.parse(startIso) }.getOrNull() ?: return false
    val end = runCatching { Instant.parse(endIso) }.getOrNull() ?: return false
    return !now.isBefore(start.minus(CHECK_IN_OPENS_BEFORE)) && !now.isAfter(end)
}

data class ScannedCheckIn(val bookingId: String, val code: String)

fun parseCheckInQr(raw: String?): ScannedCheckIn? {
    val uri = runCatching { URI(raw!!.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "tutorbridge" || uri.host != "checkin")
    {
        return null
    }

    val params = uri.rawQuery.orEmpty().split("&").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2)
        {
            parts[0] to parts[1]
        }
        else
        {
            null
        }
    }.toMap()
    val booking = params["booking"].orEmpty()
    val code = params["code"].orEmpty()
    return if (booking.isNotBlank() && code.matches(Regex("\\d{6}")))
    {
        ScannedCheckIn(booking, code)
    }
    else
    {
        null
    }
}