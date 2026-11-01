package com.tutorbridge.app

import com.tutorbridge.app.data.model.AvailabilityWindowUi
import com.tutorbridge.app.data.model.availableDays
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which weekdays a tutor can be booked on at a chosen time. */
class AvailableDaysTest {

    private fun window(day: DayOfWeek, from: String, to: String) =
        AvailabilityWindowUi(day, LocalTime.parse(from), LocalTime.parse(to))

    private val hours = listOf(
        window(MONDAY, "09:00", "17:00"),
        window(TUESDAY, "09:00", "12:00"),
        window(WEDNESDAY, "13:00", "17:00")
    )

    @Test
    fun `only days whose hours hold the whole session are open`() {
        assertEquals(setOf(MONDAY, TUESDAY), availableDays(hours, "10:00 AM"))
        assertEquals(setOf(MONDAY, WEDNESDAY), availableDays(hours, "02:00 PM"))
    }

    @Test
    fun `a session may start when the hours open and end when they close`() {
        assertEquals(setOf(MONDAY, TUESDAY), availableDays(hours, "09:00 AM"))
        assertEquals(setOf(MONDAY, WEDNESDAY), availableDays(hours, "04:00 PM"))
        assertEquals(setOf(MONDAY, TUESDAY), availableDays(hours, "11:00 AM")) // ends 12:00 exactly
    }

    @Test
    fun `a session that starts too early, ends too late or straddles an edge fits no day there`() {
        assertEquals(emptySet<DayOfWeek>(), availableDays(hours, "08:00 AM"))
        assertEquals(emptySet<DayOfWeek>(), availableDays(hours, "04:30 PM"))
        assertEquals(setOf(MONDAY), availableDays(hours, "11:30 AM")) // Tuesday closes at 12:00
    }

    @Test
    fun `a longer session needs longer hours`() {
        assertEquals(setOf(MONDAY, TUESDAY), availableDays(hours, "09:00 AM", hours = 3))
        assertEquals(setOf(MONDAY), availableDays(hours, "09:00 AM", hours = 4))
    }

    @Test
    fun `a day with several windows is open if any one holds the session`() {
        val split = listOf(window(FRIDAY, "08:00", "10:00"), window(FRIDAY, "14:00", "16:00"))
        assertEquals(setOf(FRIDAY), availableDays(split, "08:30 AM"))
        assertEquals(setOf(FRIDAY), availableDays(split, "03:00 PM"))
        assertEquals(emptySet<DayOfWeek>(), availableDays(split, "11:00 AM"))
    }

    @Test
    fun `a tutor with no hours is open on no day`() {
        assertEquals(emptySet<DayOfWeek>(), availableDays(emptyList(), "10:00 AM"))
    }

    @Test
    fun `a slot that is blank or cannot be read fits no day`() {
        assertEquals(emptySet<DayOfWeek>(), availableDays(hours, ""))
        assertEquals(emptySet<DayOfWeek>(), availableDays(hours, "not a time"))
    }

    @Test
    fun `a session that would run past midnight fits no day`() {
        val late = listOf(window(MONDAY, "00:00", "23:59"))
        assertEquals(emptySet<DayOfWeek>(), availableDays(late, "11:30 PM"))
    }
}
