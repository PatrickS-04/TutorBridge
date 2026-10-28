package com.tutorbridge.app.data.model

import java.time.Duration
import java.time.Instant

/**
 * How long ago something happened, as a fact rather than as text. The words
 * ("2 hours ago") belong to the screen, which can say them in the language the
 * user chose; building them here in English, as the repository did, froze them
 * in English and in the moment they were fetched.
 */
sealed interface RelativeTime {
    object JustNow : RelativeTime
    data class Minutes(val count: Long) : RelativeTime
    data class Hours(val count: Long) : RelativeTime
    object Yesterday : RelativeTime
    data class Days(val count: Long) : RelativeTime
}

/** Null when [iso] is missing or not a timestamp. A moment in the future reads as just now. */
fun relativeTimeOf(iso: String?, now: Instant = Instant.now()): RelativeTime? {
    val moment = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    val minutes = Duration.between(moment, now).toMinutes()
    return when {
        minutes < 1 -> RelativeTime.JustNow
        minutes < 60 -> RelativeTime.Minutes(minutes)
        minutes < 60 * 24 -> RelativeTime.Hours(minutes / 60)
        minutes < 60 * 48 -> RelativeTime.Yesterday
        else -> RelativeTime.Days(minutes / (60 * 24))
    }
}
