package com.tutorbridge.app.data.model

import java.time.LocalDate

/** A weekly series is 2 to 8 sessions, 4 being "a month". Mirrors the limits the server enforces. */
const val SERIES_MIN = 2
const val SERIES_MAX = 8
const val SERIES_DEFAULT = 4

/**
 * The dates of a weekly series starting on [first]: the same weekday each week, [count] of them.
 * The count is kept inside the allowed range rather than trusted, so a stray value from the
 * stepper can never ask the server for a series it will refuse.
 */
fun weeklyDates(first: LocalDate, count: Int): List<LocalDate> =
    (0 until count.coerceIn(SERIES_MIN, SERIES_MAX)).map { first.plusWeeks(it.toLong()) }

/** The repeat to send with a booking: null for a single session, a weekly count otherwise. */
fun repeatFor(weekly: Boolean, count: Int): RepeatDto? =
    if (weekly) RepeatDto(count = count.coerceIn(SERIES_MIN, SERIES_MAX)) else null
