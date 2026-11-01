package com.tutorbridge.app

import com.tutorbridge.app.data.model.SERIES_MAX
import com.tutorbridge.app.data.model.SERIES_MIN
import com.tutorbridge.app.data.model.toggledDays
import com.tutorbridge.app.data.model.weeksFor
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Choosing the days to book, and what that does to how many weeks can follow. */
class RepeatDaysTest {

    @Test
    fun `tapping a day that is off turns it on`() {
        assertEquals(setOf(TUESDAY), toggledDays(emptySet(), TUESDAY, MONDAY))
        assertEquals(setOf(TUESDAY, WEDNESDAY), toggledDays(setOf(TUESDAY), WEDNESDAY, MONDAY))
    }

    @Test
    fun `tapping a day that is on turns it off`() {
        assertEquals(setOf(WEDNESDAY), toggledDays(setOf(TUESDAY, WEDNESDAY), TUESDAY, MONDAY))
        assertEquals(emptySet<DayOfWeek>(), toggledDays(setOf(TUESDAY), TUESDAY, MONDAY))
    }

    @Test
    fun `the weekday of the first date can never be turned off, or added to the extras`() {
        assertEquals(setOf(TUESDAY), toggledDays(setOf(TUESDAY), MONDAY, MONDAY))
        assertEquals(emptySet<DayOfWeek>(), toggledDays(emptySet(), MONDAY, MONDAY))
    }

    @Test
    fun `the weeks stay between 2 and 8 for one day`() {
        assertEquals(SERIES_MIN, weeksFor(0, 1))
        assertEquals(4, weeksFor(4, 1))
        assertEquals(SERIES_MAX, weeksFor(50, 1))
    }

    @Test
    fun `more days allow fewer weeks, so the weeks come back into range`() {
        assertEquals(8, weeksFor(8, 3)) // 24 sessions
        assertEquals(6, weeksFor(8, 4)) // 24 sessions
        assertEquals(3, weeksFor(8, 7)) // 21 sessions
        assertEquals(2, weeksFor(2, 7))
    }

    @Test
    fun `a new state books only the weekday of the chosen date`() {
        val state = TutorBridgeUiState()
        assertEquals(emptySet<DayOfWeek>(), state.repeatDays)
    }

    @Test
    fun `the first day is the weekday of the chosen booking date`() {
        assertEquals(MONDAY, TutorBridgeUiState(selectedBookingDate = "2026-11-02").firstDay)
        assertEquals(FRIDAY, TutorBridgeUiState(selectedBookingDate = "2026-11-06").firstDay)
        assertEquals(THURSDAY, TutorBridgeUiState(selectedBookingDate = "2026-12-24").firstDay)
    }

    @Test
    fun `a date that cannot be read has no first day`() {
        assertNull(TutorBridgeUiState(selectedBookingDate = "not a date").firstDay)
    }

    @Test
    fun `the booked days are the first day and the extras, in week order`() {
        val state = TutorBridgeUiState(selectedBookingDate = "2026-11-04", repeatDays = setOf(FRIDAY, MONDAY))
        assertEquals(listOf(MONDAY, DayOfWeek.WEDNESDAY, FRIDAY), state.bookedDays)
    }
}
