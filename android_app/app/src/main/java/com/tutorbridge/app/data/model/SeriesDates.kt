package com.tutorbridge.app.data.model

import java.time.DayOfWeek
import com.tutorbridge.app.data.repository.slotTime
import java.time.LocalDate
import java.time.LocalTime

/** A weekly series is 2 to 8 sessions, 4 being "a month". Mirrors the limits the server enforces. */
const val SERIES_MIN = 2
const val SERIES_MAX = 8
const val SERIES_DEFAULT = 4

/** A pattern over several days is 1 to 7 different weekdays for 1 to 8 weeks, and never more than 24 sessions in all. */
const val SERIES_MAX_DAYS = 7
const val SERIES_MAX_SESSIONS = 24

/** The most weeks a pattern over [days] weekdays can run for without passing 24 sessions. */
fun maxWeeksFor(days: Int): Int = (SERIES_MAX_SESSIONS / days.coerceAtLeast(1)).coerceIn(1, SERIES_MAX)

/**
 * The dates of a weekly series starting on [first]: the same weekday each week, [count] of them.
 * The count is kept inside the allowed range rather than trusted, so a stray value from the
 * stepper can never ask the server for a series it will refuse.
 */
fun weeklyDates(first: LocalDate, count: Int): List<LocalDate> =
    (0 until count.coerceIn(SERIES_MIN, SERIES_MAX)).map { first.plusWeeks(it.toLong()) }

/**
 * The dates of a recurring pattern: the same time on each of [days], for [weeks] weeks, starting with [first].
 *
 * Weeks are rolling blocks of seven days counted from [first], not calendar weeks, so 3 days over 4 weeks is always
 * exactly 12 dates and no day is skipped. Each chosen weekday is its next occurrence on or after [first], repeated
 * every seven days. [first] is always the first date, whether or not its weekday is in [days], and the result is in
 * date order. This mirrors how the server counts them (the same cases are tested on both sides), so the dates shown
 * before booking are the dates that get booked.
 */
fun sessionDates(first: LocalDate, days: Set<DayOfWeek>, weeks: Int): List<LocalDate> {
    val offsets = (days + first.dayOfWeek)
        .map { (it.value - first.dayOfWeek.value + 7) % 7 }
        .distinct()
        .sorted()
    return (0 until weeks.coerceIn(1, SERIES_MAX)).flatMap { week ->
        offsets.map { offset -> first.plusDays((week * 7 + offset).toLong()) }
    }
}

/**
 * The weekdays the tutor works at the chosen [slot] (such as "02:00 PM") for [hours] hours: those with a weekly window
 * that holds the whole session. A slot that cannot be read, or that would run past midnight, fits no day.
 */
fun availableDays(windows: List<AvailabilityWindowUi>, slot: String, hours: Long = 1): Set<DayOfWeek> {
    val start = runCatching { LocalTime.parse(slot, slotTime) }.getOrNull() ?: return emptySet()
    val end = start.plusHours(hours)
    if (!end.isAfter(start)) return emptySet()
    return windows.filter { !start.isBefore(it.start) && !end.isAfter(it.end) }.map { it.day }.toSet()
}

/**
 * The extra weekdays after tapping [day]: added if it was off, dropped if it was on. The weekday of the first
 * date, [firstDay], is always booked, so tapping it changes nothing.
 */
fun toggledDays(extra: Set<DayOfWeek>, day: DayOfWeek, firstDay: DayOfWeek): Set<DayOfWeek> = when {
    day == firstDay -> extra - firstDay
    day in extra -> extra - day
    else -> extra + day
}

/** The weeks to show for [requested] when [dayCount] weekdays are booked: 2 to 8, and no more than 24 sessions in all. */
fun weeksFor(requested: Int, dayCount: Int): Int =
    requested.coerceIn(SERIES_MIN, maxWeeksFor(dayCount).coerceAtLeast(SERIES_MIN))

/**
 * How many sessions "this and following" reaches from the [index]th of a series of [count]: that session and every
 * one after it. An estimate for the confirmation message (a later session may already be cancelled); the server's
 * answer says how many were really cancelled.
 */
fun sessionsFrom(index: Int, count: Int): Int = (count - index + 1).coerceIn(1, count.coerceAtLeast(1))

/**
 * The repeat to send with a booking made on [firstDay] and, optionally, on more weekdays ([extraDays]).
 *
 * Nothing for a single session. One weekday repeated weekly is the original shape, a `count`; any more than one
 * weekday is a pattern, `days` over `weeks`. Switching weekly off with several days still books them, for one week.
 * The weeks are kept to what the server accepts: 2 to 8 when repeating, and no more than 24 sessions in all.
 */
fun repeatFor(weekly: Boolean, weeks: Int, firstDay: DayOfWeek, extraDays: Set<DayOfWeek>): RepeatDto? {
    val days = (extraDays + firstDay).sorted()
    if (days.size == 1) return if (weekly) RepeatDto(count = weeks.coerceIn(SERIES_MIN, SERIES_MAX)) else null
    val runFor = if (weekly) weeks.coerceIn(SERIES_MIN, maxWeeksFor(days.size).coerceAtLeast(SERIES_MIN)) else 1
    return RepeatDto(weeks = runFor, days = days.map { it.name })
}

/** The repeat to send with a booking: null for a single session, a weekly count otherwise. */
fun repeatFor(weekly: Boolean, count: Int): RepeatDto? =
    if (weekly) RepeatDto(count = count.coerceIn(SERIES_MIN, SERIES_MAX)) else null
