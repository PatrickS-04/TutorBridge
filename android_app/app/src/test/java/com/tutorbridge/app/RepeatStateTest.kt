package com.tutorbridge.app

import com.tutorbridge.app.data.model.SERIES_DEFAULT
import com.tutorbridge.app.data.model.repeatFor
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import java.time.DayOfWeek.MONDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A booking starts as one session, from the tutor's profile; repeating or adding days is something the student chooses. */
class RepeatStateTest {

    @Test
    fun `a new booking is a single session of the default size, from a profile`() {
        val state = TutorBridgeUiState()
        assertFalse(state.repeatWeekly)
        assertEquals(SERIES_DEFAULT, state.repeatCount)
        assertFalse(state.bookingFromApplication)
        assertEquals(0, state.lastSeriesCount)
    }

    @Test
    fun `the default state sends no repeat`() {
        val state = TutorBridgeUiState()
        assertNull(repeatFor(state.repeatWeekly, state.repeatCount, MONDAY, state.repeatDays))
    }

    @Test
    fun `switching weekly on sends the chosen number of weeks`() {
        val state = TutorBridgeUiState().copy(repeatWeekly = true, repeatCount = 6)
        assertEquals(6, repeatFor(state.repeatWeekly, state.repeatCount, MONDAY, state.repeatDays)?.count)
    }
}
